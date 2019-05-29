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
import net.ripe.rpki.validator3.storage.data.Key;
import net.ripe.rpki.validator3.storage.encoding.Coder;
import net.ripe.rpki.validator3.storage.lmdb.OnDeleteRestrictException;
import org.apache.commons.lang3.tuple.Pair;

import java.io.Serializable;
import java.nio.ByteBuffer;
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
import java.util.stream.Collectors;

import static net.ripe.rpki.validator3.storage.xodus.Xodus.byteBufferToIterable;
import static net.ripe.rpki.validator3.storage.xodus.Xodus.clearStore;
import static net.ripe.rpki.validator3.storage.xodus.Xodus.iterableToByteBuffer;
/**
 *
 *
 *
 * @param <T>
 */
public class IxMapX<T extends Serializable> extends IxBaseX<T> {

    private final Map<String, Store> indexes;
    private final Map<String, Function<T, Set<Key>>> indexFunctions;
    private final List<BiConsumer<XodusTx.Write, Key>> onDeleteTriggers = new ArrayList<>();

    public IxMapX(final Xodus xodus,
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

    public IxMapX(final Xodus xodus, String name, Coder<T> coder) {
        this(xodus, name, coder, Collections.emptyMap());
    }

    private void reindex() {
        final XodusTx.Write tx = writeTx();
        try {
            Transaction txn = tx.txn();
            indexes.forEach((name, idx) -> clearStore(txn, idx));
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

    private void dropIndexes(XodusTx.Write tx) {
        indexes.forEach((name, db) -> clearStore(tx.txn(), db));
    }

    protected StoreConfig getStoreConfig() {
        return StoreConfig.WITH_DUPLICATES;
    }

    public Optional<T> get(Key primaryKey) {
        return get(readTx(), primaryKey);
    }

    public Optional<T> get(XodusTx.Read txn, Key primaryKey) {
        verifyKey(primaryKey);
        ByteBuffer bb = iterableToByteBuffer(getMainDb().get(txn.txn(), primaryKey.toByteIterable()));
        return bb == null ?
                Optional.empty() :
                Optional.of(getValue(primaryKey, bb));
    }

    public List<T> get(XodusTx.Read txn, Set<Key> primaryKeys) {
        return primaryKeys.stream()
                .map(pk -> get(txn, pk))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
    }

    public Map<Key, T> getByIndex(String indexName, XodusTx.Read tx, Key indexKey) {
        checkNotNull(indexKey, "Index key is null");
        final ByteIterable idxKey = indexKey.toByteIterable();
        return getByIndexKeyRange(indexName, tx, idxKey, idxKey);
    }


//    public Set<Key> getPkByIndex(String indexName, XodusTx.Read tx, Key indexKey) {
//        checkNotNull(indexKey, "Index key is null");
//        final ByteBuffer idxKey = indexKey.toByteBuffer();
//        return getPkByIndexKeyRange(indexName, tx, KeyRange.closed(idxKey, idxKey));
//    }
//
//    public Map<Key, T> getByIndexLess(String indexName, XodusTx.Read tx, Key indexKey) {
//        final ByteBuffer idxKey = indexKey.toByteBuffer();
//        return getByIndexKeyRange(indexName, tx, KeyRange.lessThan(idxKey));
//    }
//
//    public Map<Key, T> getByIndexGreater(String indexName, XodusTx.Read tx, Key indexKey) {
//        final ByteBuffer idxKey = indexKey.toByteBuffer();
//        return getByIndexKeyRange(indexName, tx, KeyRange.greaterThan(idxKey));
//    }
//
//    public Set<Key> getByIndexLessPk(String indexName, XodusTx.Read tx, Key indexKey) {
//        final ByteBuffer idxKey = indexKey.toByteBuffer();
//        return getPkByIndexKeyRange(indexName, tx, KeyRange.lessThan(idxKey));
//    }
//
//    public Set<Key> getByIndexGreaterPk(String indexName, XodusTx.Read tx, Key indexKey) {
//        final ByteBuffer idxKey = indexKey.toByteBuffer();
//        return getPkByIndexKeyRange(indexName, tx, KeyRange.greaterThan(idxKey));
//    }
//
//    public Map<Key, T> getByIndexMax(String indexName, XodusTx.Read tx, Predicate<T> p) {
//        return getKeyAtTheMinOrMaxOfIndex(indexName, tx, KeyRange.allBackward(), p);
//    }
//
//    public Map<Key, T> getByIndexMin(String indexName, XodusTx.Read tx, Predicate<T> p) {
//        return getKeyAtTheMinOrMaxOfIndex(indexName, tx, KeyRange.all(), p);
//    }
//
//    Set<Key> getPkByIndexMax(String indexName, XodusTx.Read tx) {
//        return getKeyAtTheMinOrMaxOfIndex(indexName, tx, KeyRange.allBackward());
//    }
//
//    Set<Key> getPkByIndexMin(String indexName, XodusTx.Read tx) {
//        return getKeyAtTheMinOrMaxOfIndex(indexName, tx, KeyRange.all());
//    }

    public Optional<T> put(XodusTx.Write tx, Key primaryKey, T value) {
        checkKeyAndValue(primaryKey, value);
        final Transaction txn = tx.txn();
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

    public boolean modify(XodusTx.Write tx, Key primaryKey, Consumer<T> modifyValue) {
        final Optional<T> t = get(tx, primaryKey);
        t.ifPresent(v -> {
            modifyValue.accept(v);
            put(tx, primaryKey, v);
        });
        return t.isPresent();
    }

    public void delete(XodusTx.Write tx, Key primaryKey) {
        checkNotNull(primaryKey, "Key is null");
        final Transaction txn = tx.txn();
        final Store mainDb = getMainDb();
        final ByteIterable pkBuf = primaryKey.toByteIterable();
        if (indexFunctions.isEmpty()) {
            mainDb.delete(txn, pkBuf);
        } else {
            final ByteIterable bb = mainDb.get(txn, pkBuf);
            if (bb != null) {
                // TODO probably avoid deserialization, just store the
                //  index keys next to the serialized value
                final T value = getValue(primaryKey, iterableToByteBuffer(bb));
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

    public void onDelete(BiConsumer<XodusTx.Write, Key> bf) {
        onDeleteTriggers.add(bf);
    }

    @Override
    public void clear(XodusTx.Write tx) {
        clearStore(tx.txn(), getMainDb());
        dropIndexes(tx);
    }

    private Map<Key, T> getByIndexKeyRange(String indexName, XodusTx.Read tx, ByteIterable start, ByteIterable stop) {
        final Store index = getIdx(indexName);
        if (index == null) {
            return Collections.emptyMap();
        }
        final Transaction txn = tx.txn();
        final Map<Key, T> m = new HashMap<>();
        final Store mainDb = getMainDb();

        Cursor cursor = index.openCursor(txn);

        ByteIterable startKey = cursor.getSearchKeyRange(start);
        if(startKey != null){
            do{
                Key pk = new Key(cursor.getValue());
                if(!m.containsKey(pk)){
                    final ByteBuffer bb = iterableToByteBuffer(Objects.requireNonNull(mainDb.get(txn, pk.toByteIterable())));
                    if (bb != null) {
                        m.put(pk, getValue(pk, bb));
                    }
                }
            } while(cursor.getNext() && !cursor.getKey().equals(stop));
        }

        return m;
    }
/*
    private Set<Key> getPkByIndexKeyRange(String indexName, XodusTx.Read tx, KeyRange keyRange) {
        final Store index = getIdx(indexName);
        if (index == null) {
            return Collections.emptySet();
        }
        final Transaction txn = tx.txn();
        final Set<Key> values = new HashSet<>();
        try (final CursorIterator<ByteBuffer> iterator = index.iterate(txn, keyRange)) {
            while (iterator.hasNext()) {
                values.add(new Key(iterator.next().val()));
            }
        }
        return values;
    }

    private Set<Key> getKeyAtTheMinOrMaxOfIndex(String indexName, XodusTx.Read tx, KeyRange<ByteBuffer> objectKeyRange) {
        final Store index = getIdx(indexName);
        if (index != null) {
            final Transaction txn = tx.txn();
            try (final CursorIterator<ByteBuffer> iterator = index.iterate(txn, objectKeyRange)) {
                final Set<Key> primaryKeys = new HashSet<>();
                ByteBuffer currentIndexKey = null;
                while (iterator.hasNext()) {
                    final CursorIterator.KeyVal<ByteBuffer> next = iterator.next();
                    final ByteBuffer indexKey = next.key();
                    if (currentIndexKey != null) {
                        if (!currentIndexKey.equals(indexKey)) {
                            return primaryKeys;
                        }
                        primaryKeys.add(new Key(next.val()));
                    } else {
                        primaryKeys.add(new Key(next.val()));
                        currentIndexKey = indexKey.duplicate();
                    }
                }
                return primaryKeys;
            }
        }
        return Collections.emptySet();
    }

    private Map<Key, T> getKeyAtTheMinOrMaxOfIndex(String indexName, XodusTx.Read tx, KeyRange<ByteBuffer> keyRange, Predicate<T> p) {
        final Store index = getIdx(indexName);
        if (index != null) {
            final Store mainDb = getMainDb();
            final Transaction txn = tx.txn();
            try (final CursorIterator<ByteBuffer> iterator = index.iterate(txn, keyRange)) {
                final Map<Key, T> m = new HashMap<>();
                Key currentIndexKey = null;
                while (iterator.hasNext()) {
                    final CursorIterator.KeyVal<ByteBuffer> next = iterator.next();
                    final Key indexKey = new Key(next.key());
                    final ByteBuffer pkBuf = next.val();
                    if (currentIndexKey != null) {
                        if (!currentIndexKey.equals(indexKey)) {
                            return m;
                        }
                        final T value = toValue(mainDb.get(txn, pkBuf));
                        if (p.test(value)) {
                            m.put(new Key(pkBuf), value);
                        }
                    } else {
                        final T value = toValue(mainDb.get(txn, pkBuf));
                        if (p.test(value)) {
                            m.put(new Key(pkBuf), value);
                            currentIndexKey = indexKey;
                        }
                    }
                }
                return m;
            }
        }
        return Collections.emptyMap();
    }
*/
    @Override
    public IxBaseX.Sizes sizeInfo(XodusTx.Read tx) {
        IxBaseX.Sizes sizes = super.sizeInfo(tx);
        final Map<String, IxBaseX.Sizes> indexSizes = new HashMap<>();
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
            indexSizes.put(name, new IxBaseX.Sizes(count.get(), size.get(), allocatedSize));
        });
        return new Sizes(sizes.getCount(),
                sizes.getKeysAndValuesBytes(),
                sizes.getAllocatedSize(),
                indexSizes);
    }

//    public void verify(XodusTx.Read tx) {
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

    public static class Sizes extends IxBaseX.Sizes {
        @Getter
        private Map<String, IxBaseX.Sizes> indexSizes;

        @Getter
        private long totalKeysAndValuesBytes;

        @Getter
        private long totalAllocatedSize;

        Sizes(int count, long sizeInBytes, long allocatedSize, Map<String, IxBaseX.Sizes> indexSizes) {
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
