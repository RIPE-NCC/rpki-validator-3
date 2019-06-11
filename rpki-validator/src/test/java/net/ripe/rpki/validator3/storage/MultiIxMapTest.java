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
import net.ripe.rpki.validator3.storage.lmdb.Storage;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.util.Collections;
import java.util.HashSet;
import java.util.UUID;

import static net.ripe.rpki.validator3.storage.lmdb.LmdbIxMapTest.key;
import static org.junit.Assert.assertEquals;

public abstract class MultiIxMapTest {

    @Rule
    public final TemporaryFolder tmp = new TemporaryFolder();

    protected Storage storage;
    protected MultIxMap <String> multIxMap;

    @Test
    public void putAndGetBack() {
        final Key k1 = key(UUID.randomUUID());
        final Key k2 = key(UUID.randomUUID());
        final Key k3 = key(UUID.randomUUID());
        storage.writeTx0(tx -> multIxMap.put(tx, k1, "a"));

        storage.readTx0(tx -> {
            assertEquals(Sets.newHashSet("a"), new HashSet<>(multIxMap.get(tx, k1)));
            assertEquals(Sets.newHashSet("a"), new HashSet<>(multIxMap.values(tx)));
        });

        storage.writeTx0(tx -> multIxMap.put(tx, k1, "b"));

        storage.readTx0(tx -> {
            assertEquals(Sets.newHashSet("a", "b"), new HashSet<>(multIxMap.get(tx, k1)));
            assertEquals(Sets.newHashSet("a", "b"), new HashSet<>(multIxMap.values(tx)));
        });

        storage.writeTx0(tx -> {
            multIxMap.put(tx, k2, "aa");
            multIxMap.put(tx, k2, "bbb");
            multIxMap.put(tx, k2, "xxx");
        });

        storage.readTx0(tx -> {
            assertEquals(Sets.newHashSet("a", "b"), new HashSet<>(multIxMap.get(tx, k1)));
            assertEquals(Sets.newHashSet("aa", "bbb", "xxx"), new HashSet<>(multIxMap.get(tx, k2)));
            assertEquals(Sets.newHashSet("a", "b", "aa", "bbb", "xxx"), new HashSet<>(multIxMap.values(tx)));
        });
    }

    @Test
    public void putAndDelete() {
        final Key k1 = key(UUID.randomUUID());
        final Key k2 = key(UUID.randomUUID());
        final Key k3 = key(UUID.randomUUID());
        storage.writeTx0(tx -> multIxMap.put(tx, k1, "a"));

        storage.readTx0(tx -> {
            assertEquals(Sets.newHashSet("a"), new HashSet<>(multIxMap.get(tx, k1)));
            assertEquals(Sets.newHashSet("a"), new HashSet<>(multIxMap.values(tx)));
        });

        storage.writeTx0(tx -> multIxMap.delete(tx, k1));
        storage.readTx0(tx -> {
            assertEquals(Collections.emptySet(), new HashSet<>(multIxMap.get(tx, k1)));
            assertEquals(Collections.emptySet(), new HashSet<>(multIxMap.values(tx)));
        });

        storage.writeTx0(tx -> {
            multIxMap.put(tx, k1, "a");
            multIxMap.put(tx, k1, "b");
        });

        storage.writeTx0(tx -> multIxMap.delete(tx, k1, "b"));
        storage.readTx0(tx -> {
            assertEquals(Sets.newHashSet("a"), new HashSet<>(multIxMap.get(tx, k1)));
            assertEquals(Sets.newHashSet("a"), new HashSet<>(multIxMap.values(tx)));
        });

        storage.writeTx0(tx -> {
            multIxMap.put(tx, k1, "a");
            multIxMap.put(tx, k1, "b");
            multIxMap.put(tx, k2, "aa");
            multIxMap.put(tx, k2, "bb");
            multIxMap.put(tx, k2, "bb");
            multIxMap.put(tx, k2, "bb");
            multIxMap.put(tx, k2, "bb");
            multIxMap.delete(tx, k1, "b");
        });

        storage.readTx0(tx -> {
            assertEquals(Sets.newHashSet("a"), new HashSet<>(multIxMap.get(tx, k1)));
            assertEquals(Sets.newHashSet("a", "aa", "bb"), new HashSet<>(multIxMap.values(tx)));
        });
    }

}