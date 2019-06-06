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
import net.ripe.rpki.validator3.storage.Tx;
import net.ripe.rpki.validator3.storage.data.Key;
import net.ripe.rpki.validator3.storage.encoding.Coder;
import net.ripe.rpki.validator3.storage.IxMap;
import net.ripe.rpki.validator3.storage.lmdb.OnDeleteRestrictException;
import org.apache.commons.lang3.tuple.Pair;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static net.ripe.rpki.validator3.storage.xodus.Xodus.byteBufferToIterable;

/**
 *
 *
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
        Pair<Map<String, Store>, Boolean> p = xodus.createIndexes(name, indexFunctions, getStoreConfig());
        indexes = p.getLeft();
        boolean reindex = p.getRight();
        if (reindex) {
            reindex();
        }
    }

    public XodusIxMap(final Xodus xodus, String name, Coder<T> coder) {
        this(xodus, name, coder, Collections.emptyMap());
    }

    private void reindex() {
        final Tx.Write tx = writeTx();
        try {
            Transaction txn = (Transaction)tx.txn();

            indexes.forEach((name, idx) -> env.truncateStore(idx.getName(), txn));
            forEach(tx, (k, bb) -> {
                final T value = getValue(k, bb);
                indexFunctions.forEach((n, idxFun) ->
                        idxFun.apply(value).forEach(ik -> {
                            final Store idx = getIdx(n);
                            idx.put(txn, ik.toByteIterable(), k.toByteIterable());
                        }));
            });
            txn.commit();
        } finally {
            tx.close();
        }
    }

    private Store getIdx(String name) {
        checkEnv();
        return indexes.get(name);
    }

    private void dropIndexes(Tx.Write tx) {
        indexes.forEach((name, db) -> env.removeStore(db.getName(), (Transaction)tx.txn()));
        env.clear();
    }

    protected StoreConfig getStoreConfig() {
        return StoreConfig.WITH_DUPLICATES;
    }

    public Optional<T> get(Key primaryKey) {
        return get(readTx(), primaryKey);
    }

    public Optional<T> get(Tx.Read txn, Key primaryKey) {
        verifyKey(primaryKey);
        final ByteIterable bi = getMainDb().get((Transaction)txn.txn(), primaryKey.toByteIterable());
        if (bi == null) {
            return Optional.empty();
        }
        return Optional.of(getValue(primaryKey, bi.getBytesUnsafe()));
    }

    public List<T> get(Tx.Read txn, Set<Key> primaryKeys) {
        return primaryKeys.stream()
                .map(pk -> get(txn, pk))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
    }

    public Map<Key, T> getByIndex(String indexName, Tx.Read tx, Key indexKey) {
        checkNotNull(indexKey, "Index key is null");
        final ByteIterable idxKey = indexKey.toByteIterable();
        return getByIndexKeyRange(indexName, tx, idxKey, idxKey);
    }


    public Set<Key> getPkByIndex(String indexName, Tx.Read tx, Key indexKey) {
        checkNotNull(indexKey, "Index key is null");
        // TO BE DEFINED
        return Collections.emptySet();
    }

    public Map<Key, T> getByIndexLess(String indexName, Tx.Read tx, Key indexKey) {
        // TO BE DEFINED
        return Collections.emptyMap();
    }

    public Map<Key, T> getByIndexGreater(String indexName, Tx.Read tx, Key indexKey) {
        // TO BE DEFINED
        return Collections.emptyMap();
    }

    public Set<Key> getByIndexLessPk(String indexName, Tx.Read tx, Key indexKey) {
        // TO BE DEFINED
        return Collections.emptySet();
    }

    public Set<Key> getByIndexGreaterPk(String indexName, Tx.Read tx, Key indexKey) {
        // TO BE DEFINED
        return Collections.emptySet();
    }

    public Map<Key, T> getByIndexMax(String indexName, Tx.Read tx, Predicate<T> p) {
        // TO BE DEFINED
        return Collections.emptyMap();
    }

    public Map<Key, T> getByIndexMin(String indexName, Tx.Read tx, Predicate<T> p) {
        // TO BE DEFINED
        return Collections.emptyMap();
    }

    public Set<Key> getPkByIndexMax(String indexName, Tx.Read tx) {
        // TO BE DEFINED
        return Collections.emptySet();
    }

    public Set<Key> getPkByIndexMin(String indexName, Tx.Read tx) {
        // TO BE DEFINED
        return Collections.emptySet();
    }

    public Optional<T> put(Tx.Write tx, Key primaryKey, T value) {
        checkKeyAndValue(primaryKey, value);
        final Transaction txn = (Transaction)tx.txn();
        final Optional<T> oldValue = get(tx, primaryKey);
        final ByteIterable pkBuf = primaryKey.toByteIterable();
        final ByteIterable val = byteBufferToIterable(valueBuf(value));
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
                        .forEach(oik -> index.delete(txn, oik.toByteIterable()));

                indexKeys.stream()
                        .filter(ik -> !oldIndexKeys.contains(ik))
                        .forEach(ik -> index.put(txn, ik.toByteIterable(), pkBuf));
            });
            return oldValue;
        } else {
            indexFunctions.forEach((idxName, idxFun) -> {
                final Set<Key> indexKeys = idxFun.apply(value).stream()
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());

                indexKeys.forEach(ik -> getIdx(idxName).put(txn, ik.toByteIterable(), pkBuf));
            });
        }
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
        final Transaction txn = (Transaction)tx.txn();
        final Store mainDb = getMainDb();
        final ByteIterable pkBuf = primaryKey.toByteIterable();
        if (indexFunctions.isEmpty()) {
            mainDb.delete(txn, pkBuf);
        } else {
            final ByteIterable bb = mainDb.get(txn, pkBuf);
            if (bb != null) {
                // TODO probably avoid deserialization, just store the
                //  index keys next to the serialized value
                final T value = getValue(primaryKey, bb.getBytesUnsafe());
                mainDb.delete(txn, pkBuf);
                indexFunctions.forEach((idxName, idxFun) ->
                        idxFun.apply(value).forEach(ix ->
                                getIdx(idxName).delete(txn, ix.toByteIterable())));
            }
        }
        try {
            onDeleteTriggers.forEach(bf -> bf.accept(tx, primaryKey));
        } catch (OnDeleteRestrictException o) {
            tx.abort();
        }
    }

    public void onDelete(BiConsumer<Tx.Write, Key> bf) {
        onDeleteTriggers.add(bf);
    }

    @Override
    public void clear(Tx.Write tx) {
        tx.close();
        dropIndexes(tx);
    }

    @Override
    public T toValue(byte[] bb) {
        return null;
    }

    private Map<Key, T> getByIndexKeyRange(String indexName, Tx.Read tx, ByteIterable start, ByteIterable stop) {
        final Store index = getIdx(indexName);
        if (index == null) {
            return Collections.emptyMap();
        }
        final Transaction txn = (Transaction)tx.txn();
        final Map<Key, T> m = new HashMap<>();
        final Store mainDb = getMainDb();

        try (Cursor cursor = index.openCursor(txn)) {
            ByteIterable startKey = cursor.getSearchKeyRange(start);
            if (startKey != null) {
                do {
                    Key pk = new Key(cursor.getValue());
                    if (!m.containsKey(pk)) {
                        final ByteIterable obj = mainDb.get(txn, pk.toByteIterable());
                        if (obj != null) {
                            m.put(pk, getValue(pk, obj.getBytesUnsafe()));
                        }
                    }
                }
                while (cursor.getNext() && cursor.getKey().compareTo(stop) == 0);
            }
        }

        return m;
    }

    @Override
    public XodusIxBase.Sizes sizeInfo(Tx.Read tx) {
        XodusIxBase.Sizes sizes = super.sizeInfo(tx);
        final Map<String, XodusIxBase.Sizes> indexSizes = new HashMap<>();
        indexes.forEach((name, ignore) -> {
            Store idx = getIdx(name);
            AtomicInteger count = new AtomicInteger();
            AtomicInteger size = new AtomicInteger();
//            try (final CursorIterator<ByteBuffer> iterator = idx.iterate(tx.txn())) {
//                while (iterator.hasNext()) {
//                    count.incrementAndGet();
//                    final CursorIterator.KeyVal<ByteBuffer> next = iterator.next();
//                    size.addAndGet(next.key().remaining() + next.val().remaining());
//                }
//            }
            long allocatedSize = getAllocatedSize(tx, idx);
            indexSizes.put(name, new XodusIxBase.Sizes(count.get(), size.get(), allocatedSize));
        });
        return new Sizes(sizes.getCount(),
                sizes.getKeysAndValuesBytes(),
                sizes.getAllocatedSize(),
                indexSizes);
    }

//    public void verify(Tx.Read tx) {
//
//        final Map<Key, Set<Key>> indexValues = new HashMap<>();
//        forEach(tx, (k, bb) -> {
//            final T value = getValue(k, bb);
//            indexFunctions.forEach((n, idxFun) -> {
//                idxFun.apply(value).forEach(ik -> {
//                            Set<Key> pks = indexValues.get(ik);
//                            if (pks == null) {
//                                pks = getPkByIndex(n, tx, ik);
//                                indexValues.put(ik, pks);
//                            }
//                            if (!pks.contains(k)) {
//                                throw new RuntimeException("PkBuf blabla");
//                            }
//                        }
//                );
//            });
//        });
//    }

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
