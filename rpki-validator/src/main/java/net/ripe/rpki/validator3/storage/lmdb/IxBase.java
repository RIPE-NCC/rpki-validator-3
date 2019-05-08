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

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import net.ripe.rpki.validator3.storage.data.Key;
import net.ripe.rpki.validator3.storage.encoding.Coder;
import org.lmdbjava.CursorIterator;
import org.lmdbjava.Dbi;
import org.lmdbjava.DbiFlags;
import org.lmdbjava.Env;
import org.lmdbjava.Stat;

import java.io.Serializable;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;

public abstract class IxBase<T extends Serializable> {
    protected final Env<ByteBuffer> env;
    @Getter
    private final String name;

    private final Dbi<ByteBuffer> mainDb;
    final Coder<T> coder;

    IxBase(final Lmdb lmdb,
           final String name,
           final Coder<T> coder) {
        this.env = lmdb.getEnv();
        this.name = name;
        this.coder = coder;
        synchronized (lmdb) {
            this.mainDb = lmdb.createMainMapDb(name, getMainDbCreateFlags());
        }
    }

    protected abstract DbiFlags[] getMainDbCreateFlags();

    protected abstract DbiFlags[] getIndexDbiFlags();

    static void checkNotNull(Object v, String s) {
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
        return getMainDb().get(tx.txn(), key.toByteBuffer()) != null;
    }

    Dbi<ByteBuffer> getMainDb() {
        checkEnv();
        return mainDb;
    }

    void checkEnv() {
        Lmdb.checkEnv(env);
    }

    public Set<Key> keys(Tx.Read tx) {
        final Set<Key> result = new HashSet<>();
        forEach(tx, (k, v) -> result.add(k));
        return result;
    }

    public List<T> values(Tx.Read tx) {
        final List<T> result = new ArrayList<>();
        forEach(tx, (k, v) -> result.add(coder.fromBytes(v)));
        return result;
    }

    public Map<Key, T> all(Tx.Read tx) {
        final Map<Key, T> result = new HashMap<>();
        forEach(tx, (k, v) -> result.put(k, coder.fromBytes(v)));
        return result;
    }

    public void clear(Tx.Write tx) {
        getMainDb().drop(tx.txn());
    }

    public T toValue(ByteBuffer bb) {
        return coder.fromBytes(bb);
    }

    public void forEach(Tx.Read tx, BiConsumer<Key, ByteBuffer> c) {
        try (final CursorIterator<ByteBuffer> ci = getMainDb().iterate(tx.txn())) {
            while (ci.hasNext()) {
                final CursorIterator.KeyVal<ByteBuffer> next = ci.next();
                c.accept(new Key(next.key()), next.val());
            }
        }
    }

    public int deleteIf(Tx.Write tx, BiPredicate<Key, ByteBuffer> p) {
        int c = 0;
        try (final CursorIterator<ByteBuffer> ci = getMainDb().iterate(tx.txn())) {
            while (ci.hasNext()) {
                final CursorIterator.KeyVal<ByteBuffer> next = ci.next();
                if (p.test(new Key(next.key()), next.val())) {
                    ci.remove();
                    c++;
                }
            }
        }
        return c;
    }

    public long size(Tx.Read tx) {
        AtomicLong s = new AtomicLong();
        forEach(tx, (k, v) -> s.getAndIncrement());
        return s.get();
    }

    public Sizes sizeInfo(Tx.Read tx) {
        AtomicInteger count = new AtomicInteger();
        AtomicInteger size = new AtomicInteger();
        forEach(tx, (k, v) -> {
            count.getAndIncrement();
            size.addAndGet(k.size() + v.remaining());
        });
        return new Sizes(count.get(), size.get(), getAllocatedSize(tx, getMainDb()));
    }

    long getAllocatedSize(Tx.Read tx, Dbi<ByteBuffer> dbi) {
        final Stat stat = dbi.stat(tx.txn());
        return stat.pageSize * (stat.branchPages + stat.leafPages + stat.overflowPages);
    }

    @Data
    @AllArgsConstructor
    public static class Sizes {
        private int count;
        private long keysAndValuesBytes;
        private long allocatedSize;
    }
}
