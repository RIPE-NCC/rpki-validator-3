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
import net.ripe.rpki.validator3.storage.stores.LmdbRpkiObjects;
import org.lmdbjava.Cursor;
import org.lmdbjava.CursorIterator;
import org.lmdbjava.Dbi;
import org.lmdbjava.Env;
import org.lmdbjava.KeyRange;
import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static org.lmdbjava.DbiFlags.MDB_CREATE;
import static org.lmdbjava.DbiFlags.MDB_DUPFIXED;
import static org.lmdbjava.DbiFlags.MDB_DUPSORT;

/**
 * TODO
 *  Use some sort of T fingerprint to determine the situation when the definition of T is different
 *  from what is store on the disk has changed and needs to be recreated from scratch.
 *
 *
 * @param <T>
 */
public class Store<T> {
    private final Env<ByteBuffer> env;
    private final Dbi<ByteBuffer> mainDb;
    private final Coder<T> coder;
    private final Map<String, Dbi<ByteBuffer>> indexes;
    private final Map<String, Function<T, Key>> indexFunctions;

    public Store(final Env<ByteBuffer> env,
                 final String name,
                 final Coder<T> coder,
                 final Map<String, Function<T, Key>> indexFunctions) {
        this.env = env;
        this.coder = coder;
        this.mainDb = env.openDbi(name + ":main", MDB_CREATE);
        this.indexFunctions = indexFunctions;
        this.indexes = new HashMap<>();

        // TODO Add index management, reindexing if the index set has changed
        indexFunctions.forEach((n, idxFun) ->
                indexes.put(n, env.openDbi(name + ":idx:" + n, MDB_CREATE, MDB_DUPSORT, MDB_DUPFIXED)));
    }

    public Tx.Read<ByteBuffer> readTx() {
        return Tx.read(env);
    }

    public Tx.Write<ByteBuffer> writeTx() {
        return Tx.write(env);
    }

    public Optional<T> put(Key primaryKey, T value) {
        checkKeyAndValue(primaryKey, value);
        return Tx.with(Tx.write(env), tx -> put(tx, primaryKey, value));
    }

    public Optional<T> put(Tx.Write<ByteBuffer> tx, Key primaryKey, T value) {
        final ByteBuffer pkBuf = primaryKey.toByteBuffer();
        final Txn<ByteBuffer> txn = tx.txn();
        final Optional<T> oldValue = get(tx, primaryKey);
        mainDb.put(txn, pkBuf, coder.toBytes(value));
        if (oldValue.isPresent()) {
            indexFunctions.forEach((n, idxFun) -> {
                final Key oldIndexKey = idxFun.apply(oldValue.get());
                final Key indexKey = idxFun.apply(value);
                checkNotNull(indexKey, "Index key for value + " + value + " is null.");
                final Dbi<ByteBuffer> index = indexes.get(n);
                if (!oldIndexKey.equals(indexKey)) {
                    index.delete(txn, oldIndexKey.toByteBuffer(), pkBuf);
                }
                try (Cursor<ByteBuffer> c = index.openCursor(txn)) {
                    c.put(indexKey.toByteBuffer(), pkBuf);
                }
            });
            return oldValue;
        } else {
            indexFunctions.forEach((n, idxFun) -> {
                final Key indexKey = idxFun.apply(value);
                checkNotNull(indexKey, "Index key for value + " + value + " is null.");
                try (Cursor<ByteBuffer> c = indexes.get(n).openCursor(txn)) {
                    c.put(indexKey.toByteBuffer(), pkBuf);
                }
            });
            mainDb.put(txn, pkBuf, coder.toBytes(value));
        }
        return Optional.empty();
    }

    public void delete(Key primaryKey) {
        checkNotNull(primaryKey, "Key is null");
        Tx.use(Tx.write(env), tx -> delete(tx, primaryKey));
    }

    public void delete(Tx.Write<ByteBuffer> tx, Key primaryKey) {
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
                indexFunctions.forEach((n, idxFun) -> {
                    final Key indexKey = idxFun.apply(value);
                    checkNotNull(indexKey, "Index key for value + " + value + " is null.");
                    indexes.get(n).delete(txn, indexKey.toByteBuffer(), pkBuf);
                });
            }
        }
    }

    public Optional<T> get(Key primaryKey) {
        return get(readTx(), primaryKey);
    }

    public Optional<T> get(Tx.Read<ByteBuffer> txn, Key primaryKey) {
        checkNotNull(primaryKey, "Key is null");
        ByteBuffer bb = mainDb.get(txn.txn(), primaryKey.toByteBuffer());
        return bb == null ?
                Optional.empty() :
                Optional.of(coder.fromBytes(bb));
    }

    public List<T> getByIndex(String indexName, Tx.Read<ByteBuffer> tx, Key indexKey) {
        checkNotNull(indexKey, "Index key is null");
        final Dbi<ByteBuffer> index = indexes.get(indexName);
        if (index == null) {
            return Collections.emptyList();
        }

        final ByteBuffer idxKey = indexKey.toByteBuffer();
        final Txn<ByteBuffer> txn = tx.txn();
        final CursorIterator<ByteBuffer> iterator = index.iterate(txn, KeyRange.closed(idxKey, idxKey));
        final List<T> values = new ArrayList<>();
        while (iterator.hasNext()) {
            final ByteBuffer bb = mainDb.get(txn, iterator.next().val());
            if (bb != null) {
                values.add(coder.fromBytes(bb));
            }
        }
        return values;
    }

    private static void checkNotNull(Object v, String s) {
        if (v == null) {
            throw new NullPointerException(s);
        }
    }

    private void checkKeyAndValue(Key primaryKey, T value) {
        checkNotNull(primaryKey, "Key is null");
        checkNotNull(value, "Value is null");
    }

    public List<T> getAll() {
        try(Tx.Read<ByteBuffer> tx = readTx()) {
            final CursorIterator<ByteBuffer> ci = mainDb.iterate(tx.txn());
            return StreamSupport
                    .stream(ci.iterable().spliterator(), false)
                    .map(kv -> coder.fromBytes(kv.val()))
                    .collect(Collectors.toList());

        }
    }

    public void clear() {
        try(Tx.Write<ByteBuffer> tx = writeTx()) {
            erase(tx, mainDb);
            indexes.forEach((name, db) -> erase(tx, db));
        }
    }

    private void erase(Tx.Write<ByteBuffer> tx, Dbi<ByteBuffer> db) {
        final Cursor<ByteBuffer> c = db.openCursor(tx.txn());
        do {
            c.delete();
        } while (c.next());
    }
}
