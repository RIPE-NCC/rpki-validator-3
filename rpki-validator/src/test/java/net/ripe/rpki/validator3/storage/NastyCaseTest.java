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

import com.google.common.io.ByteStreams;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.commons.crypto.CertificateRepositoryObject;
import net.ripe.rpki.commons.crypto.util.CertificateRepositoryObjectFactory;
import net.ripe.rpki.commons.validation.ValidationResult;
import net.ripe.rpki.validator3.storage.data.RpkiObject;
import net.ripe.rpki.validator3.storage.encoding.custom.RpkiObjectCoder;
import net.ripe.rpki.validator3.storage.lmdb.LmdbImpl;
import net.ripe.rpki.validator3.storage.stores.impl.LmdbRpkiObject;
import net.ripe.rpki.validator3.util.Sha256;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

@RunWith(SpringRunner.class)
@Slf4j
@Ignore
public class NastyCaseTest {


    private LmdbImpl lmdb;

    private LmdbRpkiObject lmdbRpkiObject;

    @Before
    public void setUp() throws Exception {
        lmdb = new LmdbImpl("/Users/mpuzanov/ripe/tmp/rpki/validator-3/workdb/", 8192);
        lmdb.initLmdb();
        lmdbRpkiObject = new LmdbRpkiObject(lmdb);
    }

    // broken hash: F4D489D0E889F3A8156655DEF91AB90F8BD01EF019B0756CEAA91B0F979C985E
    // 277120, [0, 0, 0, 10, 0, 32, 0, 0, 0, 64, 0, 33, 0, 0, 0, 96, .....

    @Test
    public void testSearchNastyCases() {
        RpkiObjectCoder coder = new RpkiObjectCoder();
        AtomicInteger brokenCounter = new AtomicInteger(0);
        lmdb.readTx0(tx -> {
            lmdbRpkiObject.forEach(tx, (k, bb) -> {
                byte[] bytes = Bytes.toBytes(bb);
                try {
                    RpkiObject rpkiObject = coder.fromBytes(bytes);
                    assertEquals(rpkiObject.key(), k);
                    assertArrayEquals(Sha256.hash(rpkiObject.getEncoded()), rpkiObject.getSha256());
                } catch (Exception e) {
                    brokenCounter.incrementAndGet();
                    e.printStackTrace();
                }
            });
        });
        assertEquals(0, brokenCounter.get());
    }


    @Test
    public void testRealObject() throws IOException {
        InputStream is = this.getClass().getResourceAsStream("/g11HohjaKcA9vAJV9LrYPq1bKZQ.roa");
        byte[] content = ByteStreams.toByteArray(is);

        ValidationResult validationResult = ValidationResult.withLocation("whatever.roa");
        CertificateRepositoryObject repositoryObject = CertificateRepositoryObjectFactory.createCertificateRepositoryObject(content, validationResult);
        RpkiObject rpkiObject = new RpkiObject(repositoryObject);
        lmdb.writeTx0(tx -> lmdbRpkiObject.put(tx, rpkiObject));

        RpkiObjectCoder coder = new RpkiObjectCoder();
        RpkiObject rpkiObject1 = coder.fromBytes(coder.toBytes(rpkiObject));

        assertEquals(rpkiObject, rpkiObject1);
    }
}
