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

import jetbrains.exodus.ArrayByteIterable;
import jetbrains.exodus.ByteIterable;
import jetbrains.exodus.env.Environment;
import jetbrains.exodus.env.Environments;
import jetbrains.exodus.env.Store;
import net.ripe.rpki.validator3.util.Time;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static jetbrains.exodus.bindings.StringBinding.stringToEntry;
import static jetbrains.exodus.env.StoreConfig.WITHOUT_DUPLICATES;
import static org.junit.Assert.assertEquals;

@Ignore
public class XodusTest {

    @Test

    public void testXodusSpeed() throws IOException {

        //Create environment or open existing one
        final Environment env = Environments.newInstance("data");
        env.clear();

        //Create or open existing store in environment
        final Store store = env.computeInTransaction(txn -> env.openStore("MyStore", WITHOUT_DUPLICATES, txn));

        final long NREPEAT = 1000_000;


        List<ByteIterable> keys = new ArrayList<>();

        Long t = Time.timed(() -> {
            env.executeInTransaction(txn -> {

                        for (int i = 0; i < NREPEAT; i++) {
                            final ByteIterable key = randomUUIDByteIterable();
                            store.put(txn, key, stringToEntry("blabla" + key.toString()));
                            keys.add(key);
                        }
                        txn.commit();
                    }
            );
        });

        System.out.printf("Xodus storing %d uuids took %d ", NREPEAT, t);
        long counts = env.computeInTransaction(store::count);

        assertEquals(NREPEAT, counts);

        env.executeInTransaction(txn -> {
                    for (ByteIterable key : keys) {
                            assertEquals(stringToEntry("blabla" + key.toString()), store.get(txn, key));
                    }
                });

        // Close environment when we are done
        env.close();

    }

    private static ByteIterable randomUUIDByteIterable() {
        UUID uuid = makeUuid();
        ByteBuffer bb = ByteBuffer.wrap(new byte[16]);
        bb.putLong(uuid.getMostSignificantBits());
        bb.putLong(uuid.getLeastSignificantBits());

        return new ArrayByteIterable(bb.array());
    }

    private static UUID makeUuid() {
        return UUID.randomUUID();
    }

}
