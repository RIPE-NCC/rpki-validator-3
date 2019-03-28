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
package net.ripe.rpki.validator3.storage.lmdb;

import net.ripe.rpki.validator3.storage.Coder;
import net.ripe.rpki.validator3.storage.Key;
import org.lmdbjava.Cursor;
import org.lmdbjava.CursorIterator;
import org.lmdbjava.Dbi;
import org.lmdbjava.DbiFlags;
import org.lmdbjava.Env;
import org.lmdbjava.KeyRange;
import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.lmdbjava.DbiFlags.MDB_CREATE;
import static org.lmdbjava.DbiFlags.MDB_DUPSORT;

/**
 * TODO
 *  Use some sort of T fingerprint to determine the situation when the definition of T is different
 *  from what is store on the disk has changed and needs to be recreated from scratch.
 *
 *
 * @param <T>
 */
public class IxMap<T> extends IxBase<T> {

    private final Map<String, Dbi<ByteBuffer>> indexes;
    private final Map<String, Function<T, Set<Key>>> indexFunctions;
    private final List<BiConsumer<Tx.Write, Key>> onDeleteTriggers = new ArrayList<>();

    public IxMap(final Env<ByteBuffer> env,
                 final String name,
                 final Coder<T> coder,
                 final Map<String, Function<T, Set<Key>>> indexFunctions) {
        super(env, name, coder);
        this.indexFunctions = indexFunctions;

        // TODO Add index management, reindexing if the index set has changed
        indexes = new HashMap<>();
        indexFunctions.forEach((n, idxFun) ->
                indexes.put(n, env.openDbi(name + ":idx:" + n, getIndexDbiFlags())));
    }

    public IxMap(Env<ByteBuffer> env, String name, Coder<T> coder) {
        this(env, name, coder, Collections.emptyMap());
    }

    public void reindex() {
        try (Tx.Write tx = writeTx()) {
            dropIndexes(tx);
            try (final Cursor<ByteBuffer> cursor = mainDb.openCursor(tx.txn())) {
                do {
                    final T value = coder.fromBytes(cursor.val());
                    indexFunctions.forEach((n, idxFun) -> {
                        final Set<Key> indexKeys = idxFun.apply(value);
                        try (Cursor<ByteBuffer> c = indexes.get(n).openCursor(tx.txn())) {
                            indexKeys.forEach(ik -> c.put(ik.toByteBuffer(), cursor.key()));
                        }
                    });
                }
                while (cursor.next());
            }
        }
    }

    private void dropIndexes(Tx.Write tx) {
        indexes.forEach((name, db) -> db.drop(tx.txn()));
    }

    protected DbiFlags[] getMainDbCreateFlags() {
        return new DbiFlags[]{MDB_CREATE};
    }

    protected DbiFlags[] getIndexDbiFlags() {
        return new DbiFlags[]{MDB_CREATE, MDB_DUPSORT};
    }

    public Optional<T> get(Key primaryKey) {
        return get(readTx(), primaryKey);
    }

    public Optional<T> get(Tx.Read txn, Key primaryKey) {
        verifyKey(primaryKey);
        ByteBuffer bb = mainDb.get(txn.txn(), primaryKey.toByteBuffer());
        return bb == null ?
                Optional.empty() :
                Optional.of(coder.fromBytes(bb));
    }

    public List<T> getByIndex(String indexName, Tx.Read tx, Key indexKey) {
        checkNotNull(indexKey, "Index key is null");
        final ByteBuffer idxKey = indexKey.toByteBuffer();
        return getByIndexKeyRange(indexName, tx, KeyRange.closed(idxKey, idxKey));
    }

    public List<Key> getPkByIndex(String indexName, Tx.Read tx, Key indexKey) {
        checkNotNull(indexKey, "Index key is null");
        final ByteBuffer idxKey = indexKey.toByteBuffer();
        return getPkByIndexKeyRange(indexName, tx, KeyRange.closed(idxKey, idxKey));
    }

    public List<T> getByIndexLess(String indexName, Tx.Read tx, Key indexKey) {
        final ByteBuffer idxKey = indexKey.toByteBuffer();
        return getByIndexKeyRange(indexName, tx, KeyRange.lessThan(idxKey));
    }

    public List<T> getByIndexGreater(String indexName, Tx.Read tx, Key indexKey) {
        final ByteBuffer idxKey = indexKey.toByteBuffer();
        return getByIndexKeyRange(indexName, tx, KeyRange.greaterThan(idxKey));
    }

    public List<Key> getByIndexLessPk(String indexName, Tx.Read tx, Key indexKey) {
        final ByteBuffer idxKey = indexKey.toByteBuffer();
        return getPkByIndexKeyRange(indexName, tx, KeyRange.lessThan(idxKey));
    }

    public List<Key> getByIndexGreaterPk(String indexName, Tx.Read tx, Key indexKey) {
        final ByteBuffer idxKey = indexKey.toByteBuffer();
        return getPkByIndexKeyRange(indexName, tx, KeyRange.greaterThan(idxKey));
    }

    public Optional<T> put(Key primaryKey, T value) {
        checkKeyAndValue(primaryKey, value);
        return Tx.with(writeTx(), tx -> put(tx, primaryKey, value));
    }

    public Optional<T> put(Tx.Write tx, Key primaryKey, T value) {
        final ByteBuffer pkBuf = primaryKey.toByteBuffer();
        final Txn<ByteBuffer> txn = tx.txn();
        final Optional<T> oldValue = get(tx, primaryKey);
        mainDb.put(txn, pkBuf, coder.toBytes(value));
        if (oldValue.isPresent()) {
            indexFunctions.forEach((n, idxFun) -> {
                final Set<Key> oldIndexKeys = idxFun.apply(oldValue.get());
                final Set<Key> indexKeys = idxFun.apply(value).stream()
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());
                final Dbi<ByteBuffer> index = indexes.get(n);
                oldIndexKeys.stream()
                        .filter(oik -> !indexKeys.contains(oik))
                        .forEach(oik -> index.delete(txn, oik.toByteBuffer(), pkBuf));

                try (Cursor<ByteBuffer> c = index.openCursor(txn)) {
                    indexKeys.stream()
                            .filter(ik -> !oldIndexKeys.contains(ik))
                            .forEach(ik -> c.put(ik.toByteBuffer(), pkBuf));
                }
            });
            return oldValue;
        } else {
            indexFunctions.forEach((n, idxFun) -> {
                final Set<Key> indexKeys = idxFun.apply(value).stream()
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());
                try (Cursor<ByteBuffer> c = indexes.get(n).openCursor(txn)) {
                    indexKeys.forEach(ik -> c.put(ik.toByteBuffer(), pkBuf));
                }
            });
        }
        return Optional.empty();
    }

    public void delete(Key primaryKey) {
        checkNotNull(primaryKey, "Key is null");
        Tx.use(writeTx(), tx -> delete(tx, primaryKey));
    }

    public void delete(Tx.Write tx, Key primaryKey) {
        checkNotNull(primaryKey, "Key is null");
        final ByteBuffer pkBuf = primaryKey.toByteBuffer();
        final Txn<ByteBuffer> txn = tx.txn();
        if (indexFunctions.isEmpty()) {
            mainDb.delete(txn, pkBuf);
        } else {
            final ByteBuffer bb = mainDb.get(txn, pkBuf);
            if (bb != null) {
                // TODO probably avoid deserialization, just store the
                //  index keys next to the serialized value
                final T value = coder.fromBytes(bb);
                mainDb.delete(txn, pkBuf);
                indexFunctions.forEach((n, idxFun) ->
                        idxFun.apply(value).forEach(ix ->
                                indexes.get(n).delete(txn, ix.toByteBuffer(), pkBuf)));
            }
        }
        try {
            onDeleteTriggers.forEach(bf -> bf.accept(tx, primaryKey));
        } catch (OnDeleteRestrict o) {
            tx.abort();
        }
    }

    @Override
    public void clear(Tx.Write tx) {
        mainDb.drop(tx.txn());
        dropIndexes(tx);
    }

    private List<T> getByIndexKeyRange(String indexName, Tx.Read tx, KeyRange keyRange) {
        final Dbi<ByteBuffer> index = indexes.get(indexName);
        if (index == null) {
            return Collections.emptyList();
        }
        final Txn<ByteBuffer> txn = tx.txn();
        final CursorIterator<ByteBuffer> iterator = index.iterate(txn, keyRange);
        final List<T> values = new ArrayList<>();
        while (iterator.hasNext()) {
            final ByteBuffer bb = mainDb.get(txn, iterator.next().val());
            if (bb != null) {
                values.add(coder.fromBytes(bb));
            }
        }
        return values;
    }

    private List<Key> getPkByIndexKeyRange(String indexName, Tx.Read tx, KeyRange keyRange) {
        final Dbi<ByteBuffer> index = indexes.get(indexName);
        if (index == null) {
            return Collections.emptyList();
        }
        final Txn<ByteBuffer> txn = tx.txn();
        final CursorIterator<ByteBuffer> iterator = index.iterate(txn, keyRange);
        final List<Key> values = new ArrayList<>();
        while (iterator.hasNext()) {
            values.add(new Key(iterator.next().val()));
        }
        return values;
    }


    public void onDelete(BiConsumer<Tx.Write, Key> bf) {
        onDeleteTriggers.add(bf);
    }
}
