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
package net.ripe.rpki.validator3.storage;

import com.google.common.collect.Sets;
import net.ripe.rpki.validator3.storage.data.Key;
import net.ripe.rpki.validator3.util.Time;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.lmdbjava.Dbi;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public abstract class IxMapTest {

    @Rule
    public final TemporaryFolder tmp = new TemporaryFolder();

    protected IxMap<String> ixMap;

    protected static final String LENGTH_INDEX = "length-index";
    protected static final String PAIRS_INDEX = "pairs-index";


    protected abstract <T> T rtx(Function<Tx.Read, T> f);
    protected abstract <T> T wtx(Function<Tx.Write, T> f);

    protected void rtx0(Consumer<Tx.Read> f) {
        rtx(tx -> {
            f.accept(tx);
            return null;
        });
    }

    protected void wtx0(Consumer<Tx.Write> f) {
        wtx(tx -> {
            f.accept(tx);
            return null;
        });
    }

    @Test
    public void putAndGetByIndex() {
        Key ka = putAndGet("a");
        Key kaa = putAndGet("aa");
        Key kab = putAndGet("ab");
        Key kbbb = putAndGet("bbb");
        Key kxxx = putAndGet("xxx");

        rtx0(tx -> {
            assertEquals(
                    Sets.newHashSet("a", "aa", "ab", "bbb", "xxx"),
                    new HashSet<>(ixMap.values(tx)));

            assertEquals(Sets.newHashSet("a"), new HashSet<>(getByLength(tx, 1)));
            assertEquals(Sets.newHashSet("aa", "ab"), new HashSet<>(getByLength(tx, 2)));
            assertEquals(Sets.newHashSet("bbb", "xxx"), new HashSet<>(getByLength(tx, 3)));
        });
    }


    @Test
    @Ignore
    public void putAndUpdateWithBiggerValue() {
        Random r = new Random();
        wtx0(tx -> {
            for (int i = 0; i < 10_000; i++) {
                ixMap.put(tx, Key.of(i), randomString(r, 10));
            }
        });

        for (int c = 0; c < 10; c++) {
            wtx0(tx -> {
                for (int i = 0; i < 10_000; i++) {
                    final Key k = Key.of(i);
                    final String s = ixMap.get(tx, k).get();
                    ixMap.put(tx, k, s + s);
                    assertEquals(s + s, ixMap.get(tx, k).get());
                }
            });
        }
    }

    @Test
    public void putAndDelete() {
        Key ka = putAndGet("a");
        Key kaa = putAndGet("aa");
        putAndGet("ab");
        Key kbbb = putAndGet("bbb");
        putAndGet("xxx");

        wtx0(tx -> ixMap.delete(tx, ka));

        rtx0(tx -> {
            assertFalse(ixMap.get(tx, ka).isPresent());
            assertTrue(ixMap.get(tx, kaa).isPresent());
            assertTrue(ixMap.get(tx, kbbb).isPresent());
            assertEquals(Sets.newHashSet(), new HashSet<>(getByLength(tx, 1)));
            assertEquals(Sets.newHashSet("ab", "aa"), new HashSet<>(getByLength(tx, 2)));
            assertEquals(Sets.newHashSet("bbb", "xxx"), new HashSet<>(getByLength(tx, 3)));
        });

        wtx0(tx -> ixMap.delete(tx, kaa));

        rtx0(tx -> {
            assertFalse(ixMap.get(tx, kaa).isPresent());
            assertTrue(ixMap.get(tx, kbbb).isPresent());
            assertEquals(Sets.newHashSet("ab"), new HashSet<>(getByLength(tx, 2)));
            assertEquals(Sets.newHashSet("bbb", "xxx"), new HashSet<>(getByLength(tx, 3)));
        });

        wtx0(tx -> ixMap.delete(tx, kbbb));

        rtx0(tx -> {
            assertFalse(ixMap.get(tx, kbbb).isPresent());
            assertEquals(Sets.newHashSet("ab"), new HashSet<>(getByLength(tx, 2)));
            assertEquals(Sets.newHashSet("xxx"), new HashSet<>(getByLength(tx, 3)));
        });
    }

    @Test
    public void putAndUpdate() {
        Key kaa = putAndGet("aa");
        Key kbb = putAndGet("bb");
        Key kxxx = putAndGet("xxx");

        System.out.println("--------------------");
        wtx0(tx -> ixMap.put(tx, kaa, "qqq"));
        System.out.println("--------------------");
        rtx0(tx -> {
            assertEquals("qqq", ixMap.get(tx, kaa).get());
            assertEquals(Sets.newHashSet("bb"), new HashSet<>(getByLength(tx, 2)));
            assertEquals(Sets.newHashSet("qqq", "xxx"), new HashSet<>(getByLength(tx, 3)));
        });

        wtx0(tx -> ixMap.put(tx, kaa, "zz"));
        rtx0(tx -> {
            assertEquals("zz", ixMap.get(tx, kaa).get());
            assertEquals(Sets.newHashSet("zz", "bb"), new HashSet<>(getByLength(tx, 2)));
            assertEquals(Sets.newHashSet("xxx"), new HashSet<>(getByLength(tx, 3)));
        });
    }

    @Test(expected = NullPointerException.class)
    public void putAndGetNull() {
        putAndGet(null);
    }

    @Test(expected = NullPointerException.class)
    public void putAndGetNullKey() {
        wtx0(tx -> ixMap.put(tx, null, "x"));
    }


    @Test
    public void testLongOrdering() {
        final List<Long> s = positiveLongList();

        wtx0(tx ->
                s.forEach(z -> ixMap.put(tx, Key.of(z), "" + z)));

        List<String> values = ixMap.values(ixMap.readTx());
        assertEquals(s.stream().map(Object::toString).collect(Collectors.toList()), values);
    }

    private final Random random = new Random();

    @Test
    public void testLessThan() {
        final int n = 100;
        final List<String> strings = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            strings.add(randomString(random));
        }

        Long time = Time.timed(() ->
                wtx0(tx ->
                        strings.forEach(z -> ixMap.put(tx, Key.of(UUID.randomUUID()), z))));

        System.out.println("Tx time = " + time + "ms");

        rtx0(tx -> {
            for (int len = 1; len < 50; len++) {
                Map<Key, String> byIndexLess = ixMap.getByIndexLessThan(LENGTH_INDEX, tx, intKey(len));
                int finalLen = len;
                assertEquals(strings.stream()
                                .filter(s -> s.length() < finalLen)
                                .collect(Collectors.toSet()),
                        new HashSet<>(byIndexLess.values()));
            }
        });
    }

    @Test
    public void testGreaterThan() {
        final int n = 100;
        final List<String> strings = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            strings.add(randomString(random));
        }

        wtx0(tx ->
                strings.forEach(z -> ixMap.put(tx, Key.of(UUID.randomUUID()), z)));

        rtx0(tx -> {
            for (int len = 1; len < 50; len++) {
                Map<Key, String> byIndexLess = ixMap.getByIndexNotLessThan(LENGTH_INDEX, tx, intKey(len));
                int finalLen = len;
                assertEquals(strings.stream()
                                .filter(s -> s.length() >= finalLen)
                                .collect(Collectors.toSet()),
                        new HashSet<>(byIndexLess.values()));
            }
        });
    }

    @Test
    public void testMultiIndex() {
        putAndGet("xab");
        putAndGet("abx");
        putAndGet("zabx");

        rtx0(tx -> {
            assertEquals(Sets.newHashSet("xab"), new HashSet<>(getByPair(tx, "xa")));
            assertEquals(Sets.newHashSet("xab", "abx", "zabx"), new HashSet<>(getByPair(tx, "ab")));
            assertEquals(Sets.newHashSet("abx", "zabx"), new HashSet<>(getByPair(tx, "bx")));
        });
    }


    @Test
    public void testGetMinMaxByIndexWithPredicate() {
        putAndGet("a");
        putAndGet("b");
        putAndGet("xab");
        putAndGet("ttt");
        putAndGet("tt");
        putAndGet("zabx");
        putAndGet("1111");
        putAndGet("qqqq");

        rtx0(tx -> {
            assertEquals(Sets.newHashSet("zabx"), getLongestStrings(tx, s -> s.contains("z")));
            assertEquals(Sets.newHashSet("1111"), getLongestStrings(tx, s -> s.contains("1")));
            assertEquals(Sets.newHashSet("qqqq"), getLongestStrings(tx, s -> s.contains("q")));
            assertEquals(Sets.newHashSet("ttt"), getLongestStrings(tx, s -> s.contains("t")));

            assertEquals(Sets.newHashSet("b"), getShortestStrings(tx, s -> s.contains("b")));
            assertEquals(Sets.newHashSet("a"), getShortestStrings(tx, s -> s.contains("a")));
            assertEquals(Sets.newHashSet("xab"), getShortestStrings(tx, s -> s.contains("xa")));
            assertEquals(Sets.newHashSet("tt"), getShortestStrings(tx, s -> s.contains("t")));
            assertEquals(Sets.newHashSet("zabx"), getShortestStrings(tx, s -> s.contains("z")));
        });
    }

    @Test
    public void testOnDeleteCascade() {
        final Set<Key> deleteKeys = new HashSet<>();
        ixMap.onDelete((tx, k) -> deleteKeys.add(k));
        putAndGet("a");
        putAndGet("bbb");
        putAndGet("ttt");

        Key ka = Key.of("a");
        Key kb = Key.of("bbb");
        wtx0(tx -> {
                    ixMap.delete(tx, ka);
                    ixMap.delete(tx, kb);
                }
        );

        assertEquals(Sets.newHashSet(ka, kb), deleteKeys);
    }

    @Test(expected = Dbi.BadValueSizeException.class)
    public void testKeySize() {
        final String s = randomString(new Random(), 2000);
        wtx0(tx -> ixMap.put(tx, Key.of(s), s));
    }

    private Set<String> getLongestStrings(Tx.Read tx, Predicate<String> p) {
        return new HashSet<>(ixMap.getByIndexMax(LENGTH_INDEX, tx, p).values());
    }

    private Set<String> getShortestStrings(Tx.Read tx, Predicate<String> p) {
        return new HashSet<>(ixMap.getByIndexMin(LENGTH_INDEX, tx, p).values());
    }

    private Set<String> getValues(Tx.Read tx, Collection<Key> maxByIndex) {
        return maxByIndex.stream()
                .map(k -> ixMap.get(tx, k))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toSet());
    }

    private List<String> getByLength(Tx.Read tx, int i) {
        return new ArrayList<>(ixMap.getByIndex(LENGTH_INDEX, tx, intKey(i)).values());
    }

    private List<String> getByPair(Tx.Read tx, String charPair) {
        return new ArrayList<>(ixMap.getByIndex(PAIRS_INDEX, tx, Key.of(charPair)).values());
    }

    private List<Long> positiveLongList() {
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
        wtx0(tx -> ixMap.put(tx, key, v));
        rtx0(tx -> assertEquals(v, ixMap.get(tx, key).get()));
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

    public static Key key(Object o) {
        return Key.of(o.toString().getBytes());
    }

    private String randomString(Random r) {
        return randomString(r, r.nextInt(50));
    }

    public String randomString(Random r, int len) {
        StringBuilder s = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            s.append(r.nextInt(10));
        }
        return s.toString();
    }

    protected static Set<String> charPairSet(String s) {
        if (s.length() < 2) {
            return Collections.emptySet();
        }
        return IntStream.range(0, s.length() - 1)
                .mapToObj(i -> s.substring(i, i + 2))
                .collect(Collectors.toSet());
    }

}