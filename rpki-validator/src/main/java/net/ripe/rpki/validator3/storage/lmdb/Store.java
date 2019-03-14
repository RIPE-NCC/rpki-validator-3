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

import net.ripe.rpki.validator3.storage.Serializer;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

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
public class Store<T> {
    private final Env<ByteBuffer> env;
    private final Dbi<ByteBuffer> mainDb;
    private final Serializer<T> serializer;
    private final Map<String, Dbi<ByteBuffer>> indexes;
    private final Map<String, Function<T, Key>> indexFunctions;

    public Store(final Env<ByteBuffer> env,
                 final String name,
                 final Serializer<T> serializer,
                 final Map<String, Function<T, Key>> indexFunctions) {
        this.env = env;
        this.serializer = serializer;
        this.mainDb = env.openDbi(name + ":main", MDB_CREATE);
        this.indexFunctions = indexFunctions;
        this.indexes = new HashMap<>();

        // TODO Add index management, reindexing if the index set has changed
        indexFunctions.forEach((n, idxFun) ->
                indexes.put(n, env.openDbi(name + ":idx:" + n, MDB_CREATE, MDB_DUPSORT)));
    }

    public void put(Key primaryKey, T value) {
        checkKeyAndValue(primaryKey, value);
        Tx.of(env).useWriteTx(tx -> put(tx, primaryKey, value));
    }

    public void put(Tx<ByteBuffer> tx, Key primaryKey, T value) {
        checkKeyAndValue(primaryKey, value);
        final ByteBuffer pkBuf = primaryKey.toByteBuffer();
        final Txn<ByteBuffer> txn = tx.txn();
        mainDb.put(txn, pkBuf, serializer.toBytes(value));
        indexFunctions.forEach((n, idxFun) -> {
            final Key indexKey = idxFun.apply(value);
            checkNotNull(indexKey, "Index key for value + " + value + " is null.");
            try (Cursor<ByteBuffer> c = indexes.get(n).openCursor(txn)) {
                c.put(indexKey.toByteBuffer(), pkBuf);
            }
        });
    }

    public void delete(Key primaryKey) {
        checkNotNull(primaryKey, "Key is null");
        Tx.of(env).useWriteTx(tx -> delete(tx, primaryKey));
    }

    public void delete(Tx<ByteBuffer> tx, Key primaryKey) {
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
                final T value = serializer.fromBytes(bb);
                mainDb.delete(txn, pkBuf);
                indexFunctions.forEach((n, idxFun) -> {
                    final Key indexKey = idxFun.apply(value);
                    checkNotNull(indexKey, "Index key for value + " + value + " is null.");
                    indexes.get(n).delete(txn, indexKey.toByteBuffer(), pkBuf);
                });
            }
        }
    }

    public Optional<T> get(Txn<ByteBuffer> txn, Key primaryKey) {
        checkNotNull(primaryKey, "Key is null");
        ByteBuffer bb = mainDb.get(txn, primaryKey.toByteBuffer());
        return bb == null ?
                Optional.empty() :
                Optional.of(serializer.fromBytes(bb));
    }

    public List<T> getByIndex(String indexName, Txn<ByteBuffer> txn, Key indexKey) {
        checkNotNull(indexKey, "Index key is null");
        final Dbi<ByteBuffer> index = indexes.get(indexName);
        if (index == null) {
            return Collections.emptyList();
        }

        final ByteBuffer idxKey = indexKey.toByteBuffer();
        final CursorIterator<ByteBuffer> iterator = index.iterate(txn, KeyRange.closed(idxKey, idxKey));
        final List<T> values = new ArrayList<>();
        while (iterator.hasNext()) {
            final ByteBuffer bb = mainDb.get(txn, iterator.next().val());
            if (bb != null) {
                values.add(serializer.fromBytes(bb));
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
}
