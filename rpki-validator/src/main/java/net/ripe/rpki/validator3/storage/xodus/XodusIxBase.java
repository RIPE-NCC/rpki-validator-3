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

import jetbrains.exodus.ByteBufferByteIterable;
import jetbrains.exodus.ByteIterable;
import jetbrains.exodus.env.Cursor;
import jetbrains.exodus.env.Environment;
import jetbrains.exodus.env.Store;
import jetbrains.exodus.env.Transaction;
import lombok.Getter;
import net.ripe.rpki.validator3.storage.Bytes;
import net.ripe.rpki.validator3.storage.IxBase;
import net.ripe.rpki.validator3.storage.Tx;
import net.ripe.rpki.validator3.storage.data.Key;
import net.ripe.rpki.validator3.storage.encoding.Coder;

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
import java.util.zip.CRC32;

public abstract class XodusIxBase<T extends Serializable> implements IxBase<T> {

    protected final Environment env;
    @Getter
    private final String name;

    private final Store mainDb;
    final Coder<T> coder;

    XodusIxBase(final Xodus xodus,
                final String name,
                final Coder<T> coder) {
        this.env = xodus.getEnv();
        this.name = name;
        this.coder = coder;
        synchronized (xodus) {
            this.mainDb = xodus.createMainMapDb(name);
        }
    }


    static void checkNotNull(Object v, String s) {
        if (v == null) {
            throw new NullPointerException(s);
        }
    }

    public Tx.Read readTx() {
        return XodusTx.read(env);
    }

    public XodusTx.Write writeTx() {
        return XodusTx.write(env);
    }

    protected void verifyKey(Key k) {
        checkNotNull(k, "Key is null");
    }

    void checkKeyAndValue(Key primaryKey, T value) {
        verifyKey(primaryKey);
        checkNotNull(value, "Value is null");
    }

    public boolean exists(Tx.Read tx, Key key) {
        return getMainDb().get((Transaction)tx.txn(), key.toByteIterable()) != null;
    }

    Store getMainDb() {
        checkEnv();
        return mainDb;
    }

    void checkEnv() {
        Xodus.checkEnv(env);
    }

    protected ByteIterable valueBuf(T value) {
        final byte[] valueBytes = coder.toBytes(value);
        CRC32 checksum = new CRC32();
        checksum.update(valueBytes);
        ByteBuffer byteBuffer = ByteBuffer.allocate(Long.BYTES + valueBytes.length);
        byteBuffer.putLong(checksum.getValue());
        byteBuffer.put(valueBytes);
        byteBuffer.flip();
        return new ByteBufferByteIterable(byteBuffer);
    }

    protected T getValue(Key k, byte[] b) {
        ByteBuffer bb = ByteBuffer.wrap(b);
        long crc32 = bb.getLong();
        final byte[] valueBytes = Bytes.toBytes(bb);
        CRC32 checksum = new CRC32();
        checksum.update(valueBytes);
        if (checksum.getValue() != crc32) {
            throw new RuntimeException("Data for the key " + k + " is corrupted");
        }
        return coder.fromBytes(valueBytes);
    }

    public Set<Key> keys(Tx.Read tx) {
        final Set<Key> result = new HashSet<>();
        forEach(tx, (k, v) -> result.add(k));
        return result;
    }

    public List<T> values(Tx.Read tx) {
        final List<T> result = new ArrayList<>();
        forEach(tx, (k, v) -> result.add(getValue(k, v)));
        return result;
    }

    public Map<Key, T> all(Tx.Read tx) {
        final Map<Key, T> result = new HashMap<>();
        forEach(tx, (k, v) -> result.put(k, getValue(k, v)));
        return result;
    }

    public void clear(XodusTx.Write tx) {
        getMainDb().close(); //
    }

    public T toValue(ByteIterable bb) {
        return getValue(null, bb.getBytesUnsafe());
    }


    @Override
    public void forEach(Tx.Read tx, BiConsumer<Key, byte[]> c){
        try (final Cursor ci = getMainDb().openCursor((Transaction) tx.txn())) {
            while (ci.getNext()) {
                c.accept(new Key(ci.getKey()), ci.getValue().getBytesUnsafe());
            }
        }
    }

    public long size(Tx.Read tx) {
        AtomicLong s = new AtomicLong();
        forEach(tx, (k, v) -> s.getAndIncrement());
        return s.get();
    }

    @Override
    public Sizes sizeInfo(Tx.Read tx) {
        AtomicInteger count = new AtomicInteger();
        AtomicInteger size = new AtomicInteger();
        forEach(tx, (k, v) -> {
            count.getAndIncrement();
            size.addAndGet(k.size() + v.length);
        });
        return new Sizes(count.get(), size.get(), getAllocatedSize(tx, getMainDb()));
    }

    long getAllocatedSize(Tx.Read tx, Store store) {
        // TODO: Verify
        return store.count((Transaction)tx.txn());
    }


}
