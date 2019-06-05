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
import net.ripe.rpki.validator3.storage.Tx;
import net.ripe.rpki.validator3.storage.data.Key;
import net.ripe.rpki.validator3.storage.encoding.CoderFactory;
import net.ripe.rpki.validator3.util.Time;
import org.junit.Before;
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
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LmdbIxMapTest extends IxMapTest {

    @Rule
    public final TemporaryFolder tmp = new TemporaryFolder();

    private Lmdb lmdb;

    @Before
    public void setUp() throws Exception {
        lmdb = LmdbTests.makeLmdb(tmp.newFolder().getAbsolutePath());
        ixMap = lmdb.createIxMap("test",
                ImmutableMap.of(
                        LENGTH_INDEX, LmdbIxMapTest::stringLen,
                        PAIRS_INDEX, s -> charPairSet(s).stream().map(Key::of).collect(Collectors.toSet())),
                CoderFactory.makeCoder(String.class));
    }

    @Test
    public void testReindex() {
        ixMap = lmdb.createIxMap("testReindex",
                ImmutableMap.of(
                        "len", IxMapTest::stringLen,
                        "lower", s -> Key.keys(Key.of(s.toLowerCase()))),
                CoderFactory.makeCoder(String.class));

        Set<String> dbNames = lmdb.getEnv().getDbiNames().stream().map(n -> new String(n, UTF_8)).collect(Collectors.toSet());

        assertTrue(dbNames.containsAll(Sets.newHashSet("testReindex-idx-lower", "testReindex-idx-len", "testReindex-main")));

        wtx0(tx -> ixMap.put(tx, Key.of(1L), "aa"));
        wtx0(tx -> ixMap.put(tx, Key.of(2L), "aBa"));

        assertEquals(ImmutableMap.of(Key.of(1L), "aa"), lmdb.readTx(tx -> ixMap.getByIndex("len", tx, intKey(2))));
        assertEquals(ImmutableMap.of(Key.of(2L), "aBa"), lmdb.readTx(tx -> ixMap.getByIndex("len", tx, intKey(3))));
        assertEquals(ImmutableMap.of(Key.of(2L), "aBa"), lmdb.readTx(tx -> ixMap.getByIndex("lower", tx, Key.of("aba"))));

        ixMap = lmdb.createIxMap("testReindex",
                ImmutableMap.of(
                        "lenPlus1", s -> Key.keys(intKey(s.length() + 1)),
                        "lower", s -> Key.keys(Key.of(s.toLowerCase()))),
                CoderFactory.makeCoder(String.class));

        dbNames = lmdb.getEnv().getDbiNames().stream().map(n -> new String(n, UTF_8)).collect(Collectors.toSet());

        assertTrue(dbNames.containsAll(Sets.newHashSet("testReindex-idx-lower", "testReindex-idx-lenPlus1", "testReindex-main")));
        assertFalse(dbNames.contains("testReindex-idx-len"));

        assertEquals(Optional.of("aa"), lmdb.readTx(tx -> ixMap.get(tx, Key.of(1L))));
        assertEquals(Optional.of("aBa"), lmdb.readTx(tx -> ixMap.get(tx, Key.of(2L))));

        assertEquals(ImmutableMap.of(), lmdb.readTx(tx -> ixMap.getByIndex("len", tx, intKey(2))));
        assertEquals(ImmutableMap.of(Key.of(1L), "aa"), lmdb.readTx(tx -> ixMap.getByIndex("lenPlus1", tx, intKey(3))));
        assertEquals(ImmutableMap.of(Key.of(2L), "aBa"), lmdb.readTx(tx -> ixMap.getByIndex("lenPlus1", tx, intKey(4))));
        assertEquals(ImmutableMap.of(Key.of(2L), "aBa"), lmdb.readTx(tx -> ixMap.getByIndex("lower", tx, Key.of("aba"))));
    }

    @Override
    protected <T> T rtx(Function<Tx.Read, T> f) {
        return lmdb.readTx(f::apply);
    }

    @Override
    protected <T> T wtx(Function<Tx.Write, T> f) {
        return lmdb.writeTx(f::apply);
    }
}