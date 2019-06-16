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
package net.ripe.rpki.validator3.storage.encoding.custom;

import com.google.common.io.ByteStreams;
import fj.data.Either;
import lombok.extern.slf4j.Slf4j;
import net.ripe.ipresource.IpResourceSet;
import net.ripe.rpki.commons.crypto.CertificateRepositoryObject;
import net.ripe.rpki.commons.crypto.ValidityPeriod;
import net.ripe.rpki.commons.crypto.util.CertificateRepositoryObjectFactory;
import net.ripe.rpki.commons.crypto.x509cert.X509ResourceCertificateBuilder;
import net.ripe.rpki.commons.validation.ValidationResult;
import net.ripe.rpki.validator3.IntegrationTest;
import net.ripe.rpki.validator3.domain.ta.TrustAnchorsFactory;
import net.ripe.rpki.validator3.storage.data.RpkiObject;
import org.joda.time.DateTime;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.junit4.SpringRunner;

import javax.security.auth.x500.X500Principal;

import java.io.IOException;
import java.io.InputStream;
import java.security.KeyPair;

import static net.ripe.rpki.validator3.domain.ta.TrustAnchorsFactory.KEY_PAIR_FACTORY;
import static org.junit.Assert.assertEquals;

@RunWith(SpringRunner.class)
@IntegrationTest
@Slf4j
public class RpkiObjectCoderTest {

    @Test
    public void testSaveRead() {
        KeyPair generate = KEY_PAIR_FACTORY.generate();
        RpkiObject rpkiObject = new RpkiObject(
                new X509ResourceCertificateBuilder()
                        .withResources(IpResourceSet.parse("10.0.0.0/8"))
                        .withIssuerDN(new X500Principal("CN=issuer"))
                        .withSubjectDN(new X500Principal("CN=orphan"))
                        .withSerial(TrustAnchorsFactory.nextSerial())
                        .withPublicKey(generate.getPublic())
                        .withSigningKeyPair(generate)
                        .withValidityPeriod(new ValidityPeriod(DateTime.now(), DateTime.now().plusYears(1)))
                        .build());

        RpkiObjectCoder coder = new RpkiObjectCoder();
        RpkiObject rpkiObject1 = coder.fromBytes(coder.toBytes(rpkiObject));

        assertEquals(rpkiObject, rpkiObject1);
    }

    @Test
    public void testRealObject() throws IOException {
        InputStream is = this.getClass().getResourceAsStream("/557B4C46969B11E681906146C4F9AE02.roa");
        byte[] content = ByteStreams.toByteArray(is);

        ValidationResult validationResult = ValidationResult.withLocation("whatever.roa");
        CertificateRepositoryObject repositoryObject = CertificateRepositoryObjectFactory.createCertificateRepositoryObject(content, validationResult);
        RpkiObject rpkiObject = new RpkiObject(repositoryObject);

        RpkiObjectCoder coder = new RpkiObjectCoder();
        RpkiObject rpkiObject1 = coder.fromBytes(coder.toBytes(rpkiObject));

        assertEquals(rpkiObject, rpkiObject1);
    }

}