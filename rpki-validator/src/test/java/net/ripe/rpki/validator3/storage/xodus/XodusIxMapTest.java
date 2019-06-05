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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import jetbrains.exodus.env.Transaction;
import net.ripe.rpki.validator3.storage.IxMapTest;
import net.ripe.rpki.validator3.storage.Tx;
import net.ripe.rpki.validator3.storage.data.Key;
import net.ripe.rpki.validator3.storage.encoding.CoderFactory;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class XodusIxMapTest extends IxMapTest {

    @Rule
    public final TemporaryFolder tmp = new TemporaryFolder();

    private Xodus xodus;

    @Test
    public void setUp() throws Exception {
        xodus = XodusTests.makeXodus(tmp.newFolder().getAbsolutePath());
        ixMap = xodus.createIxMap("test",
                ImmutableMap.of(
                        LENGTH_INDEX, IxMapTest::stringLen,
                        PAIRS_INDEX, s -> charPairSet(s).stream().map(Key::of).collect(Collectors.toSet())),
                CoderFactory.makeCoder(String.class));
    }

    @Test
    public void testReindex() {
        ixMap = xodus.createIxMap("testReindex",
                ImmutableMap.of(
                        "len", IxMapTest::stringLen,
                        "lower", s -> Key.keys(Key.of(s.toLowerCase()))),
                CoderFactory.makeCoder(String.class));

        Set<String> dbNames = new HashSet<>(rtx(tx -> xodus.getEnv().getAllStoreNames((Transaction)tx.txn())));

        assertTrue(dbNames.containsAll(Sets.newHashSet("testReindex-idx-lower", "testReindex-idx-len", "testReindex-main")));

        wtx0(tx -> ixMap.put(tx, Key.of(1L), "aa"));
        wtx0(tx -> ixMap.put(tx, Key.of(2L), "aBa"));

        assertEquals(ImmutableMap.of(Key.of(1L), "aa"), xodus.readTx(tx -> ixMap.getByIndex("len", tx, intKey(2))));
        assertEquals(ImmutableMap.of(Key.of(2L), "aBa"), xodus.readTx(tx -> ixMap.getByIndex("len", tx, intKey(3))));
        assertEquals(ImmutableMap.of(Key.of(2L), "aBa"), xodus.readTx(tx -> ixMap.getByIndex("lower", tx, Key.of("aba"))));

        ixMap = xodus.createIxMap("testReindex",
                ImmutableMap.of(
                        "lenPlus1", s -> Key.keys(intKey(s.length() + 1)),
                        "lower", s -> Key.keys(Key.of(s.toLowerCase()))),
                CoderFactory.makeCoder(String.class));

        dbNames = new HashSet<>(rtx(tx -> xodus.getEnv().getAllStoreNames((Transaction)tx.txn())));

        assertTrue(dbNames.containsAll(Sets.newHashSet("testReindex-idx-lower", "testReindex-idx-lenPlus1", "testReindex-main")));
        assertFalse(dbNames.contains("testReindex-idx-len"));

        assertEquals(Optional.of("aa"), xodus.readTx(tx -> ixMap.get(tx, Key.of(1L))));
        assertEquals(Optional.of("aBa"), xodus.readTx(tx -> ixMap.get(tx, Key.of(2L))));

        assertEquals(ImmutableMap.of(), xodus.readTx(tx -> ixMap.getByIndex("len", tx, intKey(2))));
        assertEquals(ImmutableMap.of(Key.of(1L), "aa"), xodus.readTx(tx -> ixMap.getByIndex("lenPlus1", tx, intKey(3))));
        assertEquals(ImmutableMap.of(Key.of(2L), "aBa"), xodus.readTx(tx -> ixMap.getByIndex("lenPlus1", tx, intKey(4))));
        assertEquals(ImmutableMap.of(Key.of(2L), "aBa"), xodus.readTx(tx -> ixMap.getByIndex("lower", tx, Key.of("aba"))));
    }

    @Override
    protected <T> T rtx(Function<Tx.Read, T> f) {
        return xodus.readTx(f::apply);
    }

    @Override
    protected <T> T wtx(Function<Tx.Write, T> f) {
        return xodus.writeTx(f::apply);
    }
}