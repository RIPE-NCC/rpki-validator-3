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
package net.ripe.rpki.validator3.storage.stores.impl;

import com.google.common.collect.Sets;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.validator3.IntegrationTest;
import net.ripe.rpki.validator3.storage.data.Key;
import net.ripe.rpki.validator3.storage.data.RpkiObject;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.junit4.SpringRunner;

import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.junit.Assert.assertEquals;

@RunWith(SpringRunner.class)
@IntegrationTest
@Slf4j
@Ignore
public class LmdbDataCorruptionTest extends GenericStorageTest {

    private Random r = new Random(123);

    @Test
    public void testCorruption() {
        final Map<Key, RpkiObject> objects = new HashMap<>();

        int objectCount;
        int deltaCount = 2000;
        writeRandomObjects(objects, 10000);

        try {
            for (int i = 0; i < 1000; i++) {
                objectCount = objects.size();
                // mark half of the as they are in the future
                int finalObjectCount = objectCount;
                getLmdb().writeTx0(tx -> {
                    Set<Key> keysToUpdate = randomSubSet(objects.keySet(), finalObjectCount - deltaCount, r);
                    keysToUpdate.forEach(k -> {
                        this.getRpkiObjects().get(tx, k).ifPresent(ro -> {
                            objects.put(ro.key(), ro);
                            this.getRpkiObjects().put(tx, ro);
                        });
                    });

                    Sets.difference(objects.keySet(), keysToUpdate).forEach(k ->
                            this.getRpkiObjects().get(tx, k).ifPresent(ro -> {
                                objects.put(ro.key(), ro);
                                this.getRpkiObjects().put(tx, ro);
                            }));
                });

                // it should delete the rest
                long deletedCount = getLmdb().writeTx(tx ->
                        this.getRpkiObjects().deleteUnreachableObjects(tx, Instant.now().minus(Duration.ofHours(12))));

                List<RpkiObject> extracted = getLmdb().readTx(tx -> this.getRpkiObjects().values(tx));

                getLmdb().readTx0(tx ->
                        extracted.forEach(ro -> {
                            RpkiObject rpkiObject = objects.get(ro.key());
                            if (rpkiObject != null) {
                                assertEquals(rpkiObject, ro);
                            }
                        }));

                getLmdb().readTx0(tx ->
                        objects.forEach((sha256, rpkiObject) ->
                                this.getRpkiObjects().findBySha256(tx, sha256.getBytes()).ifPresent(ro ->
                                        assertEquals(rpkiObject, ro))));

                objects.clear();
                extracted.forEach(ro -> objects.put(ro.key(), ro));

                writeRandomObjects(objects, (int) Math.max(100, deletedCount));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void writeRandomObjects(Map<Key, RpkiObject> keys, int objectCount) {
        getLmdb().writeTx0(tx -> {
            for (int i = 0; i < objectCount; i++) {
                RpkiObject rpkiObject = randomRpkiObject();
                keys.put(rpkiObject.key(), rpkiObject);
                this.getRpkiObjects().put(tx, rpkiObject);
            }
        });
    }

    @Test
    public void testSubSet() {
        System.out.println(randomSubSet(Sets.newHashSet(1, 3, 4, 5, 6, 7), 1, r));
        System.out.println(randomSubSet(Sets.newHashSet(1, 3, 4, 5, 6, 7), 2, r));
        System.out.println(randomSubSet(Sets.newHashSet(1, 3, 4, 5, 6, 7), 2, r));
        System.out.println(randomSubSet(Sets.newHashSet(1, 3, 4, 5, 6, 7), 3, r));
        System.out.println(randomSubSet(Sets.newHashSet(1, 3, 4, 5, 6, 7), 3, r));
        System.out.println(randomSubSet(Sets.newHashSet(1, 3, 4, 5, 6, 7), 3, r));
        System.out.println(randomSubSet(Sets.newHashSet(1, 3, 4, 5, 6, 7), 4, r));
        System.out.println(randomSubSet(Sets.newHashSet(1, 3, 4, 5, 6, 7), 4, r));
        System.out.println(randomSubSet(Sets.newHashSet(1, 3, 4, 5, 6, 7), 4, r));
        System.out.println(randomSubSet(Sets.newHashSet(1, 3, 4, 5, 6, 7), 4, r));
    }

    private RpkiObject randomRpkiObject() {
        RpkiObject rpkiObject = new RpkiObject();
        rpkiObject.setAuthorityKeyIdentifier(randomBytes(32));
        rpkiObject.setSha256(randomBytes(32));
        rpkiObject.setEncoded(randomBytes(r.nextInt(5_000)));
        rpkiObject.setAuthorityKeyIdentifier(randomBytes(32));
        rpkiObject.setSigningTime(Instant.now());
        rpkiObject.setSerialNumber(new BigInteger("13"));
        rpkiObject.setType(randomElem(RpkiObject.Type.values()));
        return rpkiObject;
    }

    private <T> Set<T> randomSubSet(Set<T> s, int subSetSize, Random r) {
        int setSize = s.size();
        if (subSetSize >= setSize) {
            return s;
        }
        int counter = 0;
        Set<T> ss = new HashSet<>();
        for (T e : s) {
            if (r.nextInt(setSize) < subSetSize) {
                ss.add(e);
                if (++counter >= subSetSize) {
                    return ss;
                }
            }
        }
        return ss;
    }

    private <T> T randomElem(Collection<T> c) {
        int index = r.nextInt(c.size());
        Iterator<T> iterator = c.iterator();
        int i = 0;
        while (iterator.hasNext()) {
            T next = iterator.next();
            if (i == index) {
                return next;
            }
        }
        return null;
    }

    private <T> T randomElem(T[] c) {
        return c[r.nextInt(c.length)];
    }

    private byte[] randomBytes(int length) {
        byte[] b = new byte[length];
        r.nextBytes(b);
        return b;
    }


}