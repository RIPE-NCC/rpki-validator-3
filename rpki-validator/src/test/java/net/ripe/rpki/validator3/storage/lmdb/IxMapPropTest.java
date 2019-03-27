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
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import net.ripe.rpki.validator3.storage.FSTCoder;
import net.ripe.rpki.validator3.storage.Key;
import org.assertj.core.util.Files;
import org.hamcrest.CoreMatchers;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;
import org.lmdbjava.Env;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Optional;

import static net.ripe.rpki.validator3.storage.lmdb.IxMapTest.intKey;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeThat;
import static org.lmdbjava.Env.create;

@RunWith(JUnitQuickcheck.class)
public class IxMapPropTest {

    private static Env<ByteBuffer> env;
    private static IxMap<String> ixMap;

    private static File lmdbDir;

    @BeforeClass
    public static void setUp() {
        lmdbDir = Files.temporaryFolder();

        env = create()
                .setMapSize(1024 * 1024 * 1024L)
                .setMaxDbs(100)
                .open(lmdbDir);

        ixMap = new IxMap<>(env, "test", new FSTCoder<>(),
                ImmutableMap.of(LENGTH_INDEX, s -> Key.keys(intKey(s.length()))));
    }


    private static final String LENGTH_INDEX = "length-index";


    @Property
    public void storedIsThere(String key, String value) throws Exception {
        assumeThat(key, CoreMatchers.not(equalTo(null)));
        assumeThat(key, CoreMatchers.not(equalTo("")));
        assumeThat(value, CoreMatchers.not(equalTo(null)));

        Key k = IxMapTest.key(key);
        Optional<String> oldValue = ixMap.put(k, value);
        try (Tx.Read tx = Tx.read(env)) {
            assertEquals(value, ixMap.get(tx, k).get());
            List<String> byIndex = ixMap.getByIndex(LENGTH_INDEX, tx, intKey(value.length()));
            assertTrue(byIndex.stream().anyMatch(s -> s.equals(value)));
            oldValue.ifPresent(s1 -> {
                if (!s1.equals(value)) {
                    assertNotEquals(s1, ixMap.get(tx, k).get());
                    List<String> oldByIndex = ixMap.getByIndex(LENGTH_INDEX, tx, intKey(s1.length()));
                    assertFalse(oldByIndex.stream().anyMatch(s -> s.equals(s1)));
                }
            });
        }
    }
}
