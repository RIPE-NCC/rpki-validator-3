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
import net.ripe.rpki.validator3.storage.data.Key;
import net.ripe.rpki.validator3.storage.data.Ref;
import net.ripe.rpki.validator3.storage.data.RoaPrefix;
import net.ripe.rpki.validator3.storage.data.RpkiObject;
import net.ripe.rpki.validator3.storage.data.RpkiRepository;
import net.ripe.rpki.validator3.storage.data.TrustAnchor;
import net.ripe.rpki.validator3.storage.data.validation.CertificateTreeValidationRun;
import net.ripe.rpki.validator3.storage.data.validation.RsyncRepositoryValidationRun;
import net.ripe.rpki.validator3.storage.stores.impl.GenericStorageTest;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit4.SpringRunner;

import javax.security.auth.x500.X500Principal;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static net.ripe.rpki.validator3.domain.ta.TrustAnchorsFactory.KEY_PAIR_FACTORY;
import static org.assertj.core.api.Assertions.assertThat;


@RunWith(SpringRunner.class)
@IntegrationTest
public class ValidationRunCleanupServiceTest extends GenericStorageTest {

    @Autowired
    private TrustAnchorsFactory factory;

    @Autowired
    private ValidationRunCleanupService subject;

    private TrustAnchor testTA1;
    private Ref<TrustAnchor> testTARef1;

    private List<RoaPrefix> roaPrefixes1 = Collections.singletonList(RoaPrefix.of(IpRange.parse("127.0.0.0/8"), null, Asn.parse("123")));
    private List<RoaPrefix> roaPrefixes2 = Collections.singletonList(RoaPrefix.of(IpRange.parse("128.0.0.0/8"), null, Asn.parse("124")));

    @Before
    public void setup() {

        testTA1 = wtx(tx -> factory.createTrustAnchor(tx, ta -> ta.roaPrefixes(roaPrefixes1)));
        wtx0(tx -> getTrustAnchors().add(tx, testTA1));

        testTARef1 = getStorage().readTx(tx -> getTrustAnchors().makeRef(tx, testTA1.key()));
    }

    @Test
    public void shouldCleanUpOldValidationRun() {

        final Instant lastMonth = Instant.now().minus(Duration.ofDays(30));
        CertificateTreeValidationRun oldValidationRun =
                wtx(tx -> {
                            CertificateTreeValidationRun res = new CertificateTreeValidationRun(testTARef1);
                            res.setCreatedAt(lastMonth);
                            return res;
                        }
                );
        oldValidationRun.setCompletedAt(lastMonth);
        wtx0(tx -> getValidationRuns().add(tx, oldValidationRun));

        AtomicInteger oldCount = subject.cleanupValidationRuns().getFirst();
        assertThat(oldCount.get()).isEqualTo(1);
    }

    @Test
    public void shouldCleanUpOldValidationRunDontDeleteLastSuccessful() {

        final Instant lastMonth = Instant.now().minus(Duration.ofDays(30));
        CertificateTreeValidationRun oldValidationRun =
                wtx(tx -> {
                            CertificateTreeValidationRun res = new CertificateTreeValidationRun(testTARef1);
                            res.setCreatedAt(lastMonth);
                            res.setSucceeded();
                            return res;
                        }
                );
        oldValidationRun.setCompletedAt(lastMonth);
        wtx0(tx -> getValidationRuns().add(tx, oldValidationRun));

        AtomicInteger oldCount = subject.cleanupValidationRuns().getFirst();
        assertThat(oldCount.get()).isEqualTo(0);
    }

    @Test
    public void shouldCleanUpOldValidationRunDontDeleteLastSuccessfulPerTA() {

        TrustAnchor testTA2 = wtx(tx -> factory.createTrustAnchor(tx, ta -> ta.roaPrefixes(roaPrefixes2)));
        wtx0(tx -> getTrustAnchors().add(tx, testTA2));

        Ref<TrustAnchor> testTARef2 = getStorage().readTx(tx -> getTrustAnchors().makeRef(tx, testTA1.key()));

        final Instant lastMonth = Instant.now().minus(Duration.ofDays(30));
        CertificateTreeValidationRun oldValidationRun =
                wtx(tx -> {
                            CertificateTreeValidationRun res = new CertificateTreeValidationRun(testTARef1);
                            res.setCreatedAt(lastMonth);
                            return res;
                        }
                );
        oldValidationRun.setCompletedAt(lastMonth);
        wtx0(tx -> getValidationRuns().add(tx, oldValidationRun));

        CertificateTreeValidationRun oldValidationRun2 =
                wtx(tx -> {
                            CertificateTreeValidationRun res = new CertificateTreeValidationRun(testTARef2);
                            res.setCreatedAt(lastMonth);
                            res.setSucceeded();
                            return res;
                        }
                );
        oldValidationRun2.setCompletedAt(lastMonth);
        wtx0(tx -> getValidationRuns().add(tx, oldValidationRun2));

        AtomicInteger oldCount = subject.cleanupValidationRuns().getFirst();
        assertThat(oldCount.get()).isEqualTo(1);
    }

    @Test
    public void shouldCleanUpOrphanedAssociationWithObject() {

        // Create validation run
        RsyncRepositoryValidationRun orphanValidationRun = wtx(tx -> new RsyncRepositoryValidationRun());
        wtx0(tx -> getValidationRuns().add(tx, orphanValidationRun));

        // Create RPKI Object
        RpkiObject associatedAndDeletedObject = new RpkiObject(
                new X509ResourceCertificateBuilder()
                        .withResources(IpResourceSet.parse("10.0.0.0/8"))
                        .withIssuerDN(testTA1.getCertificate().getSubject())
                        .withSubjectDN(new X500Principal("CN=orphan"))
                        .withSerial(TrustAnchorsFactory.nextSerial())
                        .withPublicKey(KEY_PAIR_FACTORY.generate().getPublic())
                        .withSigningKeyPair(KEY_PAIR_FACTORY.generate())
                        .withValidityPeriod(new ValidityPeriod(DateTime.now(), DateTime.now().plusYears(1)))
                        .build()
        );
        wtx0(tx -> getRpkiObjects().put(tx, associatedAndDeletedObject));

        // Associate validation run with object
        wtx0(tx -> getValidationRuns().associate(tx, orphanValidationRun, associatedAndDeletedObject));

        // Delete object, validation run becomes orphan.
        wtx0(tx -> getRpkiObjects().delete(tx, associatedAndDeletedObject));

        // It should then be deleted.
        AtomicInteger orphanCount = subject.cleanupValidationRuns().getSecond();
        assertThat(orphanCount.get()).isEqualTo(1);
    }

    @Test
    public void shouldCleanUpOrphanedAssociationWithRepo() {

        RsyncRepositoryValidationRun orphanValidationRun = wtx(tx -> new RsyncRepositoryValidationRun());
        wtx0(tx -> getValidationRuns().add(tx, orphanValidationRun));

        // Prepare the repo that will be associated and then deleted.
        RpkiRepository associatedAndThenDeletedRepo = new RpkiRepository(testTARef1, testTA1.getLocations().get(0), RpkiRepository.Type.RSYNC);
        wtx0(tx -> associatedAndThenDeletedRepo.setId(Key.of(getSequences().next(tx, ":pk"))));
        wtx0(tx -> getRpkiRepositories().register(tx, testTARef1, associatedAndThenDeletedRepo.getLocationUri(), associatedAndThenDeletedRepo.getType()));

        // Associate and remove the object, validation will be orphan.
        wtx0(tx -> getValidationRuns().associate(tx, orphanValidationRun, associatedAndThenDeletedRepo));
        wtx0(tx -> getRpkiRepositories().remove(tx, associatedAndThenDeletedRepo.key()));

        AtomicInteger orphanCount = subject.cleanupValidationRuns().getSecond();
        assertThat(orphanCount.get()).isEqualTo(1);

    }
}
