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
import net.ripe.rpki.validator3.storage.FSTCoder;
import net.ripe.rpki.validator3.storage.Lmdb;
import net.ripe.rpki.validator3.storage.data.Key;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class IxMapTest {

    @Rule
    public final TemporaryFolder tmp = new TemporaryFolder();

    private Lmdb lmdb;
    private IxMap<String> ixMap;

    private static final String LENGTH_INDEX = "length-index";
    private static final String PAIRS_INDEX = "pairs-index";

    @Before
    public void setUp() throws Exception {
        lmdb = LmdbTests.makeLmdb(tmp.newFolder().getAbsolutePath());
        ixMap = new IxMap<>(lmdb.getEnv(), "test", new FSTCoder<>(),
                ImmutableMap.of(
                        LENGTH_INDEX, IxMapTest::stringLen,
                        PAIRS_INDEX, s -> charPairSet(s).stream().map(Key::of).collect(Collectors.toSet()))
        );
    }

    @Test
    public void putAndGetByIndex() {
        Key ka = putAndGet("a");
        Key kaa = putAndGet("aa");
        Key kab = putAndGet("ab");
        Key kbbb = putAndGet("bbb");
        Key kxxx = putAndGet("xxx");

        try (Tx.Read tx = lmdb.readTx()) {
            assertEquals(
                    Sets.newHashSet("a", "aa", "ab", "bbb", "xxx"),
                    new HashSet<>(ixMap.values(tx)));

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

        ixMap.delete(ka);

        try (Tx.Read tx = lmdb.readTx()) {
            assertFalse(ixMap.get(tx, ka).isPresent());
            assertTrue(ixMap.get(tx, kaa).isPresent());
            assertTrue(ixMap.get(tx, kbbb).isPresent());
            assertEquals(Sets.newHashSet(), new HashSet<>(getByLength(tx, 1)));
            assertEquals(Sets.newHashSet("ab", "aa"), new HashSet<>(getByLength(tx, 2)));
            assertEquals(Sets.newHashSet("bbb", "xxx"), new HashSet<>(getByLength(tx, 3)));
        }

        ixMap.delete(kaa);

        try (Tx.Read tx = lmdb.readTx()) {
            assertFalse(ixMap.get(tx, kaa).isPresent());
            assertTrue(ixMap.get(tx, kbbb).isPresent());
            assertEquals(Sets.newHashSet("ab"), new HashSet<>(getByLength(tx, 2)));
            assertEquals(Sets.newHashSet("bbb", "xxx"), new HashSet<>(getByLength(tx, 3)));
        }

        ixMap.delete(kbbb);

        try (Tx.Read tx = lmdb.readTx()) {
            assertFalse(ixMap.get(tx, kbbb).isPresent());
            assertEquals(Sets.newHashSet("ab"), new HashSet<>(getByLength(tx, 2)));
            assertEquals(Sets.newHashSet("xxx"), new HashSet<>(getByLength(tx, 3)));
        }
    }

    @Test
    public void putAndUpdate() {
        Key kaa = putAndGet("aa");
        Key kbb = putAndGet("bb");
        Key kxxx = putAndGet("xxx");

        ixMap.put(kaa, "qqq");
        try (Tx.Read tx = lmdb.readTx()) {
            assertEquals("qqq", ixMap.get(tx, kaa).get());
            assertEquals(Sets.newHashSet("bb"), new HashSet<>(getByLength(tx, 2)));
            assertEquals(Sets.newHashSet("qqq", "xxx"), new HashSet<>(getByLength(tx, 3)));
        }

        ixMap.put(kaa, "zz");
        try (Tx.Read tx = lmdb.readTx()) {
            assertEquals("zz", ixMap.get(tx, kaa).get());
            assertEquals(Sets.newHashSet("zz", "bb"), new HashSet<>(getByLength(tx, 2)));
            assertEquals(Sets.newHashSet("xxx"), new HashSet<>(getByLength(tx, 3)));
        }
    }

    @Test(expected = NullPointerException.class)
    public void putAndGetNull() {
        putAndGet(null);
    }

    @Test(expected = NullPointerException.class)
    public void putAndGetNullKey() {
        ixMap.put(null, "x");
    }


    @Test
    public void testLongOrdering() {
        final List<Long> s = positiveLongList();

        Tx.use(ixMap.writeTx(), tx ->
                s.forEach(z -> ixMap.put(tx, Key.of(z), "" + z)));

        List<String> values = ixMap.values(ixMap.readTx());
        assertEquals(s.stream().map(Object::toString).collect(Collectors.toList()), values);
    }

    private final Random random = new Random();

    @Test
    public void testLessThan() {
        final int n = 1000;
        final List<String> strings = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            strings.add(randomString(random));
        }

        Tx.use(ixMap.writeTx(), tx ->
                strings.forEach(z -> ixMap.put(tx, Key.of(UUID.randomUUID()), z)));

        try (Tx.Read tx = lmdb.readTx()) {
            for (int len = 1; len < 50; len++) {
                List<String> byIndexLess = ixMap.getByIndexLess(LENGTH_INDEX, tx, intKey(len));
                int finalLen = len;
                assertEquals(strings.stream()
                                .filter(s -> s.length() < finalLen)
                                .collect(Collectors.toSet()),
                        new HashSet<>(byIndexLess));
            }
        }
    }

    @Test
    public void testGreaterThan() {
        final int n = 1000;
        final List<String> strings = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            strings.add(randomString(random));
        }

        Tx.use(ixMap.writeTx(), tx ->
                strings.forEach(z -> ixMap.put(tx, Key.of(UUID.randomUUID()), z)));

        try (Tx.Read tx = lmdb.readTx()) {
            for (int len = 1; len < 50; len++) {
                List<String> byIndexLess = ixMap.getByIndexGreater(LENGTH_INDEX, tx, intKey(len));
                int finalLen = len;
                assertEquals(strings.stream()
                                .filter(s -> s.length() > finalLen)
                                .collect(Collectors.toSet()),
                        new HashSet<>(byIndexLess));
            }
        }
    }

    @Test
    public void testMultiIndex() {
        putAndGet("xab");
        putAndGet("abx");
        putAndGet("zabx");

        try (Tx.Read tx = lmdb.readTx()) {
            assertEquals(Sets.newHashSet("xab"), new HashSet<>(getByPair(tx, "xa")));
            assertEquals(Sets.newHashSet("xab", "abx", "zabx"), new HashSet<>(getByPair(tx, "ab")));
            assertEquals(Sets.newHashSet("abx", "zabx"), new HashSet<>(getByPair(tx, "bx")));
        }
    }

    private List<String> getByLength(Tx.Read tx, int i) {
        return ixMap.getByIndex(LENGTH_INDEX, tx, intKey(i));
    }

    private List<String> getByPair(Tx.Read tx, String charPair) {
        return ixMap.getByIndex(PAIRS_INDEX, tx, Key.of(charPair));
    }

    public List<Long> positiveLongList() {
        final Random r = new Random();
        final List<Long> s = new ArrayList<>();
        for (int i = 0; i < 10000; i++) {
            long z = r.nextLong();
            if (z > 0) {
                s.add(z);
            }
        }
        s.sort(Long::compareTo);
        return s;
    }


    private Key putAndGet(String v) {
        final Key key = key(UUID.randomUUID());
        ixMap.put(key, v);
        try (Tx.Read tx = lmdb.readTx()) {
            assertEquals(v, ixMap.get(tx, key).get());
        }
        return key;
    }

    public static Set<Key> stringLen(String s) {
        return Key.keys(intKey(s.length()));
    }

    public static Key intKey(int length) {
        final ByteBuffer bb = ByteBuffer.allocateDirect(Integer.BYTES);
        bb.putInt(length).flip();
        return new Key(bb);
    }

    static Key key(Object o) {
        return Key.of(o.toString().getBytes());
    }

    private String randomString(Random r) {
        final int len = r.nextInt(50);
        StringBuilder s = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            s.append(r.nextInt(10));
        }
        return s.toString();
    }

    static Set<String> charPairSet(String s) {
        if (s.length() < 2) {
            return Collections.emptySet();
        }
        return IntStream.range(0, s.length() - 1)
                .mapToObj(i -> s.substring(i, i + 2))
                .collect(Collectors.toSet());
    }

}