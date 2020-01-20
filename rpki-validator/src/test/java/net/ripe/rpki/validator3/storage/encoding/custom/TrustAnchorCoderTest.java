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

import lombok.extern.slf4j.Slf4j;
import net.ripe.ipresource.IpResourceSet;
import net.ripe.rpki.commons.crypto.ValidityPeriod;
import net.ripe.rpki.validator3.IntegrationTest;
import net.ripe.rpki.validator3.api.util.InstantWithoutNanos;
import net.ripe.rpki.validator3.domain.ta.TrustAnchorsFactory;
import net.ripe.rpki.validator3.storage.data.TrustAnchor;
import net.ripe.rpki.validator3.storage.stores.impl.GenericStorageTest;
import org.joda.time.Duration;
import org.joda.time.Instant;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit4.SpringRunner;

import java.security.KeyPair;
import java.util.Collections;

import static net.ripe.rpki.validator3.domain.ta.TrustAnchorsFactory.KEY_PAIR_FACTORY;
import static net.ripe.rpki.validator3.domain.ta.TrustAnchorsFactory.TA_RRDP_NOTIFY_URI;
import static org.junit.Assert.assertEquals;

@RunWith(SpringRunner.class)
@IntegrationTest
@Slf4j
public class TrustAnchorCoderTest extends GenericStorageTest {

    @Autowired
    private TrustAnchorsFactory trustAnchorsFactory;

    @Test
    public void testSaveRead() {
        TrustAnchor trustAnchor = makeTa();

        TrustAnchorCoder coder = new TrustAnchorCoder();
        TrustAnchor trustAnchor1 = coder.fromBytes(coder.toBytes(trustAnchor));

        assertEquals(trustAnchor, trustAnchor1);
    }

    public TrustAnchor makeTa() {
        final KeyPair childKeyPair = KEY_PAIR_FACTORY.generate();

        final ValidityPeriod mftValidityPeriod = new ValidityPeriod(
                Instant.now().minus(Duration.standardDays(2)),
                Instant.now().minus(Duration.standardDays(1))
        );

        final InstantWithoutNanos now = InstantWithoutNanos.now();

        return wtx(tx -> {
            TrustAnchor ta1 = trustAnchorsFactory.createTrustAnchor(tx, x -> {
                TrustAnchorsFactory.CertificateAuthority child = TrustAnchorsFactory.CertificateAuthority.builder()
                        .dn("CN=child-ca")
                        .keyPair(childKeyPair)
                        .certificateLocation("rsync://rpki.test/CN=child-ca.cer")
                        .resources(IpResourceSet.parse("192.168.128.0/17"))
                        .notifyURI(TA_RRDP_NOTIFY_URI)
                        .manifestURI("rsync://rpki.test/CN=child-ca/child-ca.mft")
                        .repositoryURI("rsync://rpki.test/CN=child-ca/")
                        .crlDistributionPoint("rsync://rpki.test/CN=child-ca/child-ca.crl")
                        .build();
                x.children(Collections.singletonList(child));
            }, mftValidityPeriod);
            ta1.setInitialCertificateTreeValidationRunCompleted(true);
            ta1.setUpdatedAt(now);
            ta1.setCreatedAt(now.minus(java.time.Duration.ofDays(1)));
            this.getTrustAnchors().add(tx, ta1);
            return ta1;
        });
    }
}