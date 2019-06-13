/**
 * The BSD License
 *
 * Copyright (c) 2010-2018 RIPE NCC
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *   - Redistributions of source code must retain the above copyright notice,
 *     this list of conditions and the following disclaimer.
 *   - Redistributions in binary form must reproduce the above copyright notice,
 *     this list of conditions and the following disclaimer in the documentation
 *     and/or other materials provided with the distribution.
 *   - Neither the name of the RIPE NCC nor the names of its contributors may be
 *     used to endorse or promote products derived from this software without
 *     specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package net.ripe.rpki.validator3.storage.xodus;

import jetbrains.exodus.ByteIterable;
import jetbrains.exodus.env.Cursor;
import jetbrains.exodus.env.Store;
import jetbrains.exodus.env.StoreConfig;
import jetbrains.exodus.env.Transaction;
import lombok.Getter;
import net.ripe.rpki.validator3.storage.Bytes;
import net.ripe.rpki.validator3.storage.IxMap;
import net.ripe.rpki.validator3.storage.Tx;
import net.ripe.rpki.validator3.storage.data.Key;
import net.ripe.rpki.validator3.storage.encoding.Coder;
import net.ripe.rpki.validator3.storage.OnDeleteRestrictException;
import org.apache.commons.lang3.tuple.Pair;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 *
 * @param <T>
 */
public class XodusIxMap<T extends Serializable> extends XodusIxBase<T> implements IxMap<T> {

    private final Map<String, Store> indexes;
    private final Map<String, Function<T, Set<Key>>> indexFunctions;
    private final List<BiConsumer<Tx.Write, Key>> onDeleteTriggers = new ArrayList<>();

    public XodusIxMap(final Xodus xodus,
                      final String name,
                      final Coder<T> coder,
                      final Map<String, Function<T, Set<Key>>> indexFunctions) {
        super(xodus, name, coder);
        this.indexFunctions = indexFunctions;
        Pair<Map<String, Store>, Boolean> p = xodus.createIndexes(name, indexFunctions, StoreConfig.WITH_DUPLICATES_WITH_PREFIXING);
        indexes = p.getLeft();
        boolean reindex = p.getRight();
        if (reindex) {
            reindex();
        }
    }

    private void reindex() {
        this.env.executeInExclusiveTransaction(txn -> {
            indexes.forEach((name, idx) -> env.truncateStore(idx.getName(), txn));
            try (final Cursor ci = getMainDb().openCursor(txn)) {
                while (ci.getNext()) {
                    ByteIterable pk = ci.getKey();
                    final T value = getValue(new Key(pk), Bytes.toBytes(ci.getValue()));
                    indexFunctions.forEach((n, idxFun) -> {
                        final Store idx = getIdx(n);
                        idxFun.apply(value).forEach(ik -> idx.put(txn, ik.toByteIterable(), pk));
                    });
                }
            }
        });
    }

    private Store getIdx(String name) {
        checkEnv();
        return indexes.get(name);
    }

    private void dropIndexes(Tx.Write tx) {
        indexes.forEach((name, db) -> truncate(tx, db));
    }

    protected StoreConfig getStoreConfig() {
        return StoreConfig.WITHOUT_DUPLICATES_WITH_PREFIXING;
    }

    public Optional<T> get(Key primaryKey) {
        return get(readTx(), primaryKey);
    }

    public Optional<T> get(Tx.Read tx, Key primaryKey) {
        verifyKey(primaryKey);
        final ByteIterable bi = getMainDb().get(castTxn(tx), primaryKey.toByteIterable());
        if (bi == null) {
            return Optional.empty();
        }
        return Optional.of(getValue(primaryKey, Bytes.toBytes(bi)));
    }

    public List<T> get(Tx.Read txn, Set<Key> primaryKeys) {
        return primaryKeys.stream()
                .map(pk -> get(txn, pk))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
    }

    public Optional<T> put(Tx.Write tx, Key primaryKey, T value) {
        checkKeyAndValue(primaryKey, value);
        final Transaction txn = castTxn(tx);
        final Optional<T> oldValue = get(tx, primaryKey);
        final ByteIterable pkBuf = primaryKey.toByteIterable();
        final ByteIterable val = valueWithChecksum(value);

        getMainDb().put(txn, pkBuf, val);
        if (oldValue.isPresent()) {
            indexFunctions.forEach((idxName, idxFun) -> {
                final Set<Key> oldIndexKeys = idxFun.apply(oldValue.get());
                final Set<Key> indexKeys = idxFun.apply(value).stream()
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());
                final Store index = getIdx(idxName);
                oldIndexKeys.stream()
                        .filter(oik -> !indexKeys.contains(oik))
                        .forEach(oik -> {
                            try (Cursor c = index.openCursor(txn)) {
                                if (c.getSearchBoth(oik.toByteIterable(), pkBuf)) {
                                    c.deleteCurrent();
                                }
                            }
                        });

                indexKeys.stream()
                        .filter(ik -> !oldIndexKeys.contains(ik))
                        .forEach(ik -> index.put(txn, ik.toByteIterable(), pkBuf));
            });
            return oldValue;
        }
        indexFunctions.forEach((idxName, idxFun) -> {
            final Set<Key> indexKeys = idxFun.apply(value).stream()
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            indexKeys.forEach(ik -> getIdx(idxName).put(txn, ik.toByteIterable(), pkBuf));
        });

        return Optional.empty();
    }

    public boolean modify(Tx.Write tx, Key primaryKey, Consumer<T> modifyValue) {
        final Optional<T> t = get(tx, primaryKey);
        t.ifPresent(v -> {
            modifyValue.accept(v);
            put(tx, primaryKey, v);
        });
        return t.isPresent();
    }

    public void delete(Tx.Write tx, Key primaryKey) {
        checkNotNull(primaryKey, "Key is null");
        final Transaction txn = castTxn(tx);
        final Store mainDb = getMainDb();
        final ByteIterable pkBuf = primaryKey.toByteIterable();

        if (indexFunctions.isEmpty()) {
            mainDb.delete(txn, pkBuf);
        } else {
            final ByteIterable bb = mainDb.get(txn, pkBuf);
            if (bb != null) {
                // TODO probably avoid deserialization, just store the
                //  index keys next to the serialized value
                final T value = getValue(primaryKey, Bytes.toBytes(bb));
                mainDb.delete(txn, pkBuf);
                indexFunctions.forEach((idxName, idxFun) ->
                        idxFun.apply(value).forEach(ix -> {
                            try (Cursor c = getIdx(idxName).openCursor(txn)) {
                                if (c.getSearchBoth(ix.toByteIterable(), pkBuf)) {
                                    c.deleteCurrent();
                                }
                            }
                        }));
            }
        }
        try {
            onDeleteTriggers.forEach(bf -> bf.accept(tx, primaryKey));
        } catch (OnDeleteRestrictException o) {
            tx.abort();
        }
    }


    public Map<Key, T> getByIndex(String indexName, Tx.Read tx, Key indexKey) {
        return values(tx, getPkByIndex(indexName, tx, indexKey));
    }

    public Map<Key, T> values(Tx.Read tx, Set<Key> pks) {
        final Map<Key, T> m = new HashMap<>();
        pks.forEach(pk -> get(tx, pk).ifPresent(v -> m.put(pk, v)));
        return m;
    }

    public Set<Key> getPkByIndex(String indexName, Tx.Read tx, Key indexKey) {
        checkNotNull(indexKey, "Index key is null");
        final ByteIterable idxKey = indexKey.toByteIterable();
        return getPkByIndexKeyRange(indexName, tx, idxKey, idxKey);
    }

    public Map<Key, T> getByIndexLessThan(String indexName, Tx.Read tx, Key indexKey) {
        return values(tx, getPkByIndexLessThan(indexName, tx, indexKey));
    }

    public Map<Key, T> getByIndexNotLessThan(String indexName, Tx.Read tx, Key indexKey) {
        return values(tx, getPkByIndexGreaterThan(indexName, tx, indexKey));
    }

    public Set<Key> getPkByIndexLessThan(String indexName, Tx.Read tx, Key indexKey) {
        checkNotNull(indexKey, "Index key is null");
        final ByteIterable idxKey = indexKey.toByteIterable();
        return getPkByIndexKeyRange(indexName, tx, null, idxKey);
    }

    public Set<Key> getPkByIndexGreaterThan(String indexName, Tx.Read tx, Key indexKey) {
        checkNotNull(indexKey, "Index key is null");
        final ByteIterable idxKey = indexKey.toByteIterable();
        return getPkByIndexKeyRange(indexName, tx, idxKey, null);
    }

    public Map<Key, T> getByIdxDescendingWhere(String indexName, Tx.Read tx, Predicate<T> p) {
        return getOrderedMapWhere(indexName, tx, false, p);
    }

    public Map<Key, T> getByIdxAscendingWhere(String indexName, Tx.Read tx, Predicate<T> p) {
        return getOrderedMapWhere(indexName, tx, true, p);
    }

    private Map<Key, T> getOrderedMapWhere(String indexName, Tx.Read tx,
                                           boolean ascending,
                                           Predicate<T> predicate) {

        Function<Cursor, Boolean> getStart = c -> ascending ? c.getNext() : c.getLast();
        Function<Cursor, Boolean> getNextValue = c -> ascending ? c.getNextDup() : c.getPrevDup();
        Function<Cursor, Boolean> getNextIndex = c -> ascending ? c.getNext() : c.getPrev();

        Store index = getIdx(indexName);
        final Map<Key, T> m = new HashMap<>();
        if (index != null) {
            Store mainDb = getMainDb();
            Transaction txn = castTxn(tx);
            try (Cursor cursor = index.openCursor(txn)) {
                boolean hasNextIndexKey = getStart.apply(cursor);
                boolean foundResult = false;
                while (hasNextIndexKey) {
                    final ByteIterable pk = cursor.getValue();
                    ByteIterable bi = mainDb.get(txn, pk);
                    if (bi != null) {
                        final T value = toValue(bi);
                        if (predicate.test(value)) {
                            foundResult = true;
                            m.put(new Key(pk), value);
                        }
                    }
                    if (foundResult) {
                        hasNextIndexKey = getNextValue.apply(cursor);
                    } else {
                        hasNextIndexKey = getNextIndex.apply(cursor);
                    }
                }
            }
        }
        return m;
    }

    public void onDelete(BiConsumer<Tx.Write, Key> bf) {
        onDeleteTriggers.add(bf);
    }

    @Override
    public void clear(Tx.Write tx) {
        truncate(tx, getMainDb());
        dropIndexes(tx);
    }

    @Override
    public T toValue(byte[] bb) {
        return getValue(null, bb);
    }

    private Set<Key> getPkByIndexKeyRange(String indexName, Tx.Read tx, ByteIterable start, ByteIterable stop) {
        final Store index = getIdx(indexName);
        if (index == null) {
            return Collections.emptySet();
        }
        final Transaction txn = castTxn(tx);
        final Set<Key> pks = new HashSet<>();
        try (Cursor cursor = index.openCursor(txn)) {
            if (start == null) {
                if (stop == null) {
                    // This is actually getting everything.
                    while (cursor.getNext()) {
                        pks.add(new Key(cursor.getValue()));
                    }
                } else {
                    while (cursor.getNext() && cursor.getKey().compareTo(stop) < 0) {
                        pks.add(new Key(cursor.getValue()));
                    }
                }
            } else {
                if (stop == null) {
                    ByteIterable startKey = cursor.getSearchKeyRange(start);
                    if (startKey != null) {
                        pks.add(new Key(cursor.getValue()));
                        while (cursor.getNext()) {
                            pks.add(new Key(cursor.getValue()));
                        }
                    }
                } else {
                    if (start.equals(stop)) {
                        // special case of exact match
                        ByteIterable startKey = cursor.getSearchKey(start);
                        if (startKey != null) {
                            pks.add(new Key(cursor.getValue()));
                            while (cursor.getNextDup()) {
                                pks.add(new Key(cursor.getValue()));
                            }
                        }
                    } else {
                        ByteIterable startKey = cursor.getSearchKeyRange(start);
                        if (startKey != null) {
                            pks.add(new Key(cursor.getValue()));
                            while (cursor.getNext() && cursor.getKey().compareTo(stop) < 0) {
                                pks.add(new Key(cursor.getValue()));
                            }
                        }
                    }
                }
            }
        }
        return pks;
    }

    @Override
    public XodusIxBase.Sizes sizeInfo(Tx.Read tx) {
        XodusIxBase.Sizes sizes = super.sizeInfo(tx);
        final Map<String, XodusIxBase.Sizes> indexSizes = new HashMap<>();
        indexes.forEach((name, ignore) -> {
            Store idx = getIdx(name);
            AtomicInteger count = new AtomicInteger();
            AtomicInteger size = new AtomicInteger();
            long allocatedSize = getAllocatedSize(tx, idx);
            indexSizes.put(name, new XodusIxBase.Sizes(count.get(), size.get(), allocatedSize));
        });
        return new Sizes(sizes.getCount(),
                sizes.getKeysAndValuesBytes(),
                sizes.getAllocatedSize(),
                indexSizes);
    }


    public static class Sizes extends XodusIxBase.Sizes {
        @Getter
        private Map<String, XodusIxBase.Sizes> indexSizes;

        @Getter
        private long totalKeysAndValuesBytes;

        @Getter
        private long totalAllocatedSize;

        Sizes(int count, long sizeInBytes, long allocatedSize, Map<String, XodusIxBase.Sizes> indexSizes) {
            super(count, sizeInBytes, allocatedSize);
            this.indexSizes = indexSizes.isEmpty() ? null : indexSizes;
            totalKeysAndValuesBytes = sizeInBytes;
            totalAllocatedSize = allocatedSize;
            indexSizes.forEach((n, s) -> {
                totalKeysAndValuesBytes += s.getKeysAndValuesBytes();
                totalAllocatedSize += s.getAllocatedSize();
            });
        }
    }

}
