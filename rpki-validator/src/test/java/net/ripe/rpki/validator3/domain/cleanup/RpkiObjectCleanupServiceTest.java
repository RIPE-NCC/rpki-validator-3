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
package net.ripe.rpki.validator3.domain.cleanup;

import net.ripe.ipresource.Asn;
import net.ripe.ipresource.IpRange;
import net.ripe.ipresource.IpResourceSet;
import net.ripe.rpki.commons.crypto.ValidityPeriod;
import net.ripe.rpki.commons.crypto.x509cert.X509ResourceCertificateBuilder;
import net.ripe.rpki.validator3.IntegrationTest;
import net.ripe.rpki.validator3.domain.ta.TrustAnchorsFactory;
import net.ripe.rpki.validator3.storage.data.RoaPrefix;
import net.ripe.rpki.validator3.storage.data.RpkiObject;
import net.ripe.rpki.validator3.storage.data.TrustAnchor;
import net.ripe.rpki.validator3.storage.stores.RpkiObjects;
import net.ripe.rpki.validator3.storage.stores.impl.GenericStorageTest;
import org.joda.time.DateTime;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit4.SpringRunner;

import javax.security.auth.x500.X500Principal;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;

import static net.ripe.rpki.validator3.domain.ta.TrustAnchorsFactory.KEY_PAIR_FACTORY;
import static org.assertj.core.api.Assertions.assertThat;

@RunWith(SpringRunner.class)
@IntegrationTest
public class RpkiObjectCleanupServiceTest extends GenericStorageTest {

    @Autowired
    private TrustAnchorsFactory factory;

    @Autowired
    private RpkiObjectCleanupService subject;

    @Autowired
    private RpkiObjects rpkiObjects;

    @Test
    public void should_delete_objects_not_reachable_from_manifest() throws Exception {

        TrustAnchor trustAnchor = wtx(tx -> factory.createTrustAnchor(tx, ta ->
                ta.roaPrefixes(Collections.singletonList(RoaPrefix.of(IpRange.parse("127.0.0.0/8"), null, Asn.parse("123"))))));

        // No orphans, so nothing to delete
        assertThat(subject.cleanupRpkiObjects()).isEqualTo(0);

        RpkiObject orphan = new RpkiObject(
            new X509ResourceCertificateBuilder()
                .withResources(IpResourceSet.parse("10.0.0.0/8"))
                .withIssuerDN(trustAnchor.getCertificate().getSubject())
                .withSubjectDN(new X500Principal("CN=orphan"))
                .withSerial(TrustAnchorsFactory.nextSerial())
                .withPublicKey(KEY_PAIR_FACTORY.generate().getPublic())
                .withSigningKeyPair(KEY_PAIR_FACTORY.generate())
                .withValidityPeriod(new ValidityPeriod(DateTime.now(), DateTime.now().plusYears(1)))
                .build()
        );
        wtx0(tx -> rpkiObjects.put(tx, orphan));

        // Orphan is still new, so nothing to delete
        assertThat(subject.cleanupRpkiObjects()).isEqualTo(0);

        wtx0(tx -> rpkiObjects.markReachable(tx, orphan.key(), Instant.now().minus(Duration.ofDays(10))));

        // Orphan is now old, so should be deleted
        assertThat(subject.cleanupRpkiObjects()).isEqualTo(1);
    }
}
