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

import lombok.Getter;
import net.ripe.rpki.validator3.storage.Coder;
import net.ripe.rpki.validator3.storage.Key;
import org.lmdbjava.CursorIterator;
import org.lmdbjava.Dbi;
import org.lmdbjava.DbiFlags;
import org.lmdbjava.Env;

import java.io.Serializable;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

public abstract class IxBase<T extends Serializable> {
    private final Env<ByteBuffer> env;
    @Getter
    private final String name;

    final Dbi<ByteBuffer> mainDb;
    final Coder<T> coder;

    public IxBase(final Env<ByteBuffer> env,
                  final String name,
                  final Coder<T> coder) {
        this.env = env;
        this.name = name;
        this.coder = coder;
        this.mainDb = env.openDbi(name + ":main", getMainDbCreateFlags());
    }

    protected abstract DbiFlags[] getMainDbCreateFlags();

    protected abstract DbiFlags[] getIndexDbiFlags();

    protected static void checkNotNull(Object v, String s) {
        if (v == null) {
            throw new NullPointerException(s);
        }
    }

    public Tx.Read readTx() {
        return Tx.read(env);
    }

    public Tx.Write writeTx() {
        return Tx.write(env);
    }

    protected void verifyKey(Key k) {
        checkNotNull(k, "Key is null");
    }

    void checkKeyAndValue(Key primaryKey, T value) {
        verifyKey(primaryKey);
        checkNotNull(value, "Value is null");
    }

    public boolean exists(Tx.Read tx, Key key) {
        return mainDb.get(tx.txn(), key.toByteBuffer()) != null;
    }

    public List<T> values(Tx.Read tx) {
        final List<T> result = new ArrayList<>();
        try (final CursorIterator<ByteBuffer> ci = mainDb.iterate(tx.txn())) {
            while (ci.hasNext()) {
                result.add(coder.fromBytes(ci.next().val()));
            }
        }
        return result;
    }

    public Map<Key, T> all(Tx.Read tx) {
        final Map<Key, T> result = new HashMap<>();
        try (final CursorIterator<ByteBuffer> ci = mainDb.iterate(tx.txn())) {
            while (ci.hasNext()) {
                CursorIterator.KeyVal<ByteBuffer> next = ci.next();
                result.put(new Key(next.key()), coder.fromBytes(next.val()));
            }
        }
        return result;
    }

    public void clear(Tx.Write tx) {
        mainDb.drop(tx.txn());
    }

    public T toValue(ByteBuffer bb) {
        return coder.fromBytes(bb);
    }

    public void forEach(Tx.Read tx, BiConsumer<Key, ByteBuffer> c) {
        try (final CursorIterator<ByteBuffer> ci = mainDb.iterate(tx.txn())) {
            while (ci.hasNext()) {
                final CursorIterator.KeyVal<ByteBuffer> next = ci.next();
                c.accept(new Key(next.key()), next.val());
            }
        }
    }

    // TODO Optimize
    public long size(Tx.Read tx) {
        long s = 0;
        try (final CursorIterator<ByteBuffer> ci = mainDb.iterate(tx.txn())) {
            while (ci.hasNext()) {
                s++;
            }
        }
        return s;
    }

}
