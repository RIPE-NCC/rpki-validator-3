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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import net.ripe.rpki.validator3.storage.Bytes;
import net.ripe.rpki.validator3.storage.FSTCoder;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.lmdbjava.Env;
import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.lmdbjava.Env.create;

public class StoreTest {

    @Rule
    public final TemporaryFolder tmp = new TemporaryFolder();

    private Env<ByteBuffer> env;
    private Store<String> store;

    private static final String LENGTH_INDEX = "length-index";

    @Before
    public void setUp() throws Exception {
        env = create()
                .setMapSize(1024 * 1024 * 1024L)
                .setMaxDbs(100)
                .open(tmp.newFolder());

        store = new Store<>(env, "test", new FSTCoder<>(), ImmutableMap.of(LENGTH_INDEX, StoreTest::stringLen));
    }

    @Test
    public void putAndGetByIndex() {
        Key ka = putAndGet("a");
        Key kaa = putAndGet("aa");
        Key kab = putAndGet("ab");
        Key kbbb = putAndGet("bbb");
        Key kxxx = putAndGet("xxx");

        assertEquals(
                Sets.newHashSet("a", "aa", "ab", "bbb", "xxx"),
                new HashSet<>(store.getAll()));

        try (Tx.Read<ByteBuffer> tx = Tx.read(env)) {
            assertEquals(Sets.newHashSet("a"), new HashSet<>(getByLength(tx, 1)));
            assertEquals(Sets.newHashSet("aa", "ab"), new HashSet<>(getByLength(tx, 2)));
            assertEquals(Sets.newHashSet("bbb", "xxx"), new HashSet<>(getByLength(tx, 3)));
        }
    }

    @Test
    public void putAndDelete() {
        Key ka = putAndGet("a");
        Key kaa = putAndGet("aa");
        putAndGet("ab");
        Key kbbb = putAndGet("bbb");
        putAndGet("xxx");

        store.delete(ka);

        try (Tx.Read<ByteBuffer> tx = Tx.read(env)) {
            assertFalse(store.get(tx, ka).isPresent());
            assertTrue(store.get(tx, kaa).isPresent());
            assertTrue(store.get(tx, kbbb).isPresent());
            assertEquals(Sets.newHashSet(), new HashSet<>(getByLength(tx, 1)));
            assertEquals(Sets.newHashSet("ab", "aa"), new HashSet<>(getByLength(tx, 2)));
            assertEquals(Sets.newHashSet("bbb", "xxx"), new HashSet<>(getByLength(tx, 3)));
        }

        store.delete(kaa);

        try (Tx.Read<ByteBuffer> tx = Tx.read(env)) {
            assertFalse(store.get(tx, kaa).isPresent());
            assertTrue(store.get(tx, kbbb).isPresent());
            assertEquals(Sets.newHashSet("ab"), new HashSet<>(getByLength(tx, 2)));
            assertEquals(Sets.newHashSet("bbb", "xxx"), new HashSet<>(getByLength(tx, 3)));
        }

        store.delete(kbbb);

        try (Tx.Read<ByteBuffer> tx = Tx.read(env)) {
            assertFalse(store.get(tx, kbbb).isPresent());
            assertEquals(Sets.newHashSet("ab"), new HashSet<>(getByLength(tx, 2)));
            assertEquals(Sets.newHashSet("xxx"), new HashSet<>(getByLength(tx, 3)));
        }
    }

    @Test
    public void putAndUpdate() {
        Key kaa = putAndGet("aa");
        Key kbb = putAndGet("bb");
        Key kxxx = putAndGet("xxx");

        store.put(kaa, "qqq");
        try (Tx.Read<ByteBuffer> tx = Tx.read(env)) {
            assertEquals("qqq", store.get(tx, kaa).get());
            assertEquals(Sets.newHashSet("bb"), new HashSet<>(getByLength(tx, 2)));
            assertEquals(Sets.newHashSet("qqq", "xxx"), new HashSet<>(getByLength(tx, 3)));
        }

        store.put(kaa, "zz");
        try (Tx.Read<ByteBuffer> tx = Tx.read(env)) {
            assertEquals("zz", store.get(tx, kaa).get());
            assertEquals(Sets.newHashSet("zz", "bb"), new HashSet<>(getByLength(tx, 2)));
            assertEquals(Sets.newHashSet("xxx"), new HashSet<>(getByLength(tx, 3)));
        }
    }

    private List<String> getByLength(Tx.Read<ByteBuffer> tx, int i) {
        return store.getByIndex(LENGTH_INDEX, tx, intKey(i));
    }

    @Test(expected = NullPointerException.class)
    public void putAndGetNull() {
        putAndGet(null);
    }

    @Test(expected = NullPointerException.class)
    public void putAndGetNullKey() {
        store.put(null, "x");
    }

    private Key putAndGet(String v) {
        final Key key = key(UUID.randomUUID());
        store.put(key, v);
        try (Tx.Read<ByteBuffer> tx = Tx.read(env)) {
            assertEquals(v, store.get(tx, key).get());
        }
        return key;
    }

    public static Key stringLen(String s) {
        return intKey(s.length());
    }

    public static Key intKey(int length) {
        final ByteBuffer bb = ByteBuffer.allocateDirect(Integer.BYTES);
        bb.putInt(length).flip();
        return new Key(bb);
    }

    static Key key(Object o) {
        return new Key(Bytes.toDirectBuffer(o.toString().getBytes()));
    }

}