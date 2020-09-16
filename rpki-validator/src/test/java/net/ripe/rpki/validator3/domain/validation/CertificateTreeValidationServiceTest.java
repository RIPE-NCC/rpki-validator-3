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
package net.ripe.rpki.validator3.domain.validation;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import net.ripe.ipresource.Asn;
import net.ripe.ipresource.IpAddress;
import net.ripe.ipresource.IpRange;
import net.ripe.ipresource.IpResourceSet;
import net.ripe.rpki.commons.crypto.ValidityPeriod;
import net.ripe.rpki.commons.crypto.x509cert.X509ResourceCertificate;
import net.ripe.rpki.commons.validation.ValidationResult;
import net.ripe.rpki.commons.validation.ValidationString;
import net.ripe.rpki.validator3.background.ValidationScheduler;
import net.ripe.rpki.validator3.domain.ta.TrustAnchorsFactory;
import net.ripe.rpki.validator3.storage.data.Ref;
import net.ripe.rpki.validator3.storage.data.RoaPrefix;
import net.ripe.rpki.validator3.storage.data.RpkiObject;
import net.ripe.rpki.validator3.storage.data.RpkiRepository;
import net.ripe.rpki.validator3.storage.data.TrustAnchor;
import net.ripe.rpki.validator3.storage.data.validation.CertificateTreeValidationRun;
import net.ripe.rpki.validator3.storage.data.validation.ValidationCheck;
import net.ripe.rpki.validator3.storage.stores.impl.GenericStorageTest;
import org.apache.commons.lang3.tuple.Pair;
import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.joda.time.Instant;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import javax.security.auth.x500.X500Principal;
import java.net.URI;
import java.security.KeyPair;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;
import static net.ripe.rpki.validator3.domain.ta.TrustAnchorsFactory.CertificateAuthority;
import static net.ripe.rpki.validator3.domain.ta.TrustAnchorsFactory.KEY_PAIR_FACTORY;
import static net.ripe.rpki.validator3.domain.ta.TrustAnchorsFactory.TA_CA_REPOSITORY_URI;
import static net.ripe.rpki.validator3.domain.ta.TrustAnchorsFactory.TA_RRDP_NOTIFY_URI;
import static net.ripe.rpki.validator3.storage.data.validation.ValidationRun.Status.SUCCEEDED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;

@RunWith(SpringRunner.class)
@ActiveProfiles(profiles="test")
@SpringBootTest(properties = "rpki.validator.strict-validation=true")
public class CertificateTreeValidationServiceTest extends GenericStorageTest {

    @Autowired
    private TrustAnchorsFactory factory;

    @Autowired
    private CertificateTreeValidationService subject;

    @Autowired
    private ValidationScheduler validationScheduler;

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        validationScheduler.disable();
    }

    @Test
    public void should_register_rpki_repositories() {
        TrustAnchor ta = factory.createRipeNccTrustAnchor();
        wtx(tx -> this.getTrustAnchors().add(tx, ta));

        subject.validate(ta.key().asLong());

        List<CertificateTreeValidationRun> completed = rtx(tx -> this.getValidationRuns().findAll(tx, CertificateTreeValidationRun.class));
        assertThat(completed).hasSize(1);

        CertificateTreeValidationRun result = completed.get(0);
        assertThat(result.getStatus()).isEqualTo(SUCCEEDED);

        rtx0(tx -> {
            final List<RpkiRepository> all = this.getRpkiRepositories().findAll(tx, ta.key()).collect(toList());
            assertEquals(RpkiRepository.Status.PENDING, all.get(0).getStatus());
            assertEquals("https://rrdp.ripe.net/notification.xml", all.get(0).getLocationUri());
            assertThat(ta.isInitialCertificateTreeValidationRunCompleted()).as("trust anchor initial validation run completed").isFalse();
            assertThat(this.getSettings().isInitialValidationRunCompleted(tx)).as("validator initial validation run completed").isFalse();
        });

    }

    @Test
    public void should_register_rsync_repositories() {
        TrustAnchor ta = wtx(tx -> {
            TrustAnchor trustAnchor = factory.createTrustAnchor(tx, x -> {
                x.notifyURI(null);
                x.repositoryURI(TA_CA_REPOSITORY_URI);
            });
            this.getTrustAnchors().add(tx, trustAnchor);
            return trustAnchor;
        });

        subject.validate(ta.key().asLong());

        List<CertificateTreeValidationRun> completed = rtx(tx -> this.getValidationRuns().findAll(tx, CertificateTreeValidationRun.class));
        assertThat(completed).hasSize(1);

        CertificateTreeValidationRun result = completed.get(0);
        assertThat(result.getStatus()).isEqualTo(SUCCEEDED);

        rtx0(tx -> {
            assertThat(this.getRpkiRepositories().findAll(tx, ta.key())).first().extracting(
                    RpkiRepository::getStatus,
                    RpkiRepository::getLocationUri
            ).containsExactly(
                    RpkiRepository.Status.PENDING,
                    TA_CA_REPOSITORY_URI
            );
            assertThat(ta.isInitialCertificateTreeValidationRunCompleted()).as("trust anchor initial validation run completed").isFalse();
            assertThat(this.getSettings().isInitialValidationRunCompleted(tx)).as("validator initial validation run completed").isFalse();
        });

    }

    @Test
    public void should_validate_minimal_trust_anchor() {
        TrustAnchor ta = wtx(tx -> {
            TrustAnchor trustAnchor = factory.createTrustAnchor(tx, x -> {
                x.notifyURI(TA_RRDP_NOTIFY_URI);
                x.repositoryURI(TA_CA_REPOSITORY_URI);
            });
            this.getTrustAnchors().add(tx, trustAnchor);
            final Ref<TrustAnchor> trustAnchorRef = this.getTrustAnchors().makeRef(tx, trustAnchor.key());
            RpkiRepository repository = this.getRpkiRepositories().register(tx, trustAnchorRef, TA_RRDP_NOTIFY_URI, RpkiRepository.Type.RRDP);
            repository.setDownloaded();
            this.getRpkiRepositories().update(tx, repository);
            this.getTrustAnchors().add(tx, trustAnchor);
            return trustAnchor;
        });

        subject.validate(ta.key().asLong());

        List<CertificateTreeValidationRun> completed = rtx(tx -> this.getValidationRuns().findAll(tx, CertificateTreeValidationRun.class));
        assertThat(completed).hasSize(1);

        CertificateTreeValidationRun result = completed.get(0);
        assertThat(result.getValidationChecks()).isEmpty();
        assertThat(result.getStatus()).isEqualTo(SUCCEEDED);

        Set<RpkiObject> validatedObjects = rtx(tx ->
                this.getValidationRuns().findAssociatedPks(tx, result)
                        .stream()
                        .map(pk -> this.getRpkiObjects().get(tx, pk))
                        .filter(Optional::isPresent)
                        .map(Optional::get)
                        .collect(Collectors.toSet()));

        rtx0(tx ->
                assertEquals(
                        Sets.newHashSet(
                                "rsync://rpki.test/test-trust-anchor.mft",
                                "rsync://rpki.test/test-trust-anchor.crl"
                        ),
                        validatedObjects.stream()
                                .flatMap(ro -> this.getRpkiObjects().getLocations(tx, ro.key()).stream())
                                .collect(Collectors.toSet())));

        rtx0(tx -> {
            TrustAnchor trustAnchor = this.getTrustAnchors().get(tx, ta.key()).get();
            assertThat(trustAnchor.isInitialCertificateTreeValidationRunCompleted()).as("trust anchor initial validation run completed").isTrue();
            assertThat(this.getSettings().isInitialValidationRunCompleted(tx)).as("validator initial validation run completed").isFalse();
        });
    }

    @Test
    public void should_validate_child_ca() {
        KeyPair childKeyPair = KEY_PAIR_FACTORY.generate();

        TrustAnchor ta = wtx(tx -> factory.createTrustAnchor(tx, x -> {
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
            x.children(Arrays.asList(child));
        }));

        wtx0(tx -> {
            this.getTrustAnchors().add(tx, ta);
            final Ref<TrustAnchor> trustAnchorRef = this.getTrustAnchors().makeRef(tx, ta.key());
            RpkiRepository repository = this.getRpkiRepositories().register(tx, trustAnchorRef, TA_RRDP_NOTIFY_URI, RpkiRepository.Type.RRDP);
            repository.setDownloaded();
            this.getRpkiRepositories().update(tx, repository);
        });

        subject.validate(ta.key().asLong());

        List<CertificateTreeValidationRun> completed = rtx(tx -> this.getValidationRuns().findAll(tx, CertificateTreeValidationRun.class));
        assertThat(completed).hasSize(1);

        rtx0(tx -> {
            final Optional<TrustAnchor> trustAnchor = this.getTrustAnchors().get(tx, ta.key());
            assertThat(trustAnchor.get().isInitialCertificateTreeValidationRunCompleted()).as("trust anchor initial validation run completed").isTrue();
            assertThat(this.getSettings().isInitialValidationRunCompleted(tx)).as("validator initial validation run completed").isTrue();
        });

        List<Pair<CertificateTreeValidationRun, RpkiObject>> validated = rtx(tx ->
                this.getValidationRuns().findCurrentlyValidated(tx, RpkiObject.Type.CER).collect(toList()));
        assertThat(validated).hasSize(1);
        assertThat(validated.get(0).getLeft()).isEqualTo(completed.get(0));
        Optional<X509ResourceCertificate> cro = validated.get(0).getRight().get(X509ResourceCertificate.class, ValidationResult.withLocation("ignored.cer"));
        assertThat(cro).isPresent().hasValueSatisfying(x -> assertThat(x.getSubject()).isEqualTo(new X500Principal("CN=child-ca")));
    }

    @Test
    public void should_report_proper_error_when_repository_is_unavailable() {
        TrustAnchor trustAnchor = wtx(tx -> {
            TrustAnchor ta = factory.createTypicalTa(tx, KEY_PAIR_FACTORY.generate());
            this.getTrustAnchors().add(tx, ta);
            return ta;
        });

        RpkiRepository repository = wtx(tx -> {
            final Ref<TrustAnchor> trustAnchorRef = this.getTrustAnchors().makeRef(tx, trustAnchor.key());
            RpkiRepository r = this.getRpkiRepositories().register(tx, trustAnchorRef, TA_RRDP_NOTIFY_URI, RpkiRepository.Type.RRDP);
            r.setFailed();
            this.getRpkiRepositories().update(tx, r);

            final URI manifestUri = trustAnchor.getCertificate().getManifestUri();
            final Optional<RpkiObject> mft = this.getRpkiObjects().values(tx)
                    .stream()
                    .filter(o -> this.getRpkiObjects().getLocations(tx, o.key()).contains(manifestUri.toASCIIString()))
                    .findFirst();
            mft.ifPresent(m -> this.getRpkiObjects().delete(tx, m));
            return r;
        });

        subject.validate(trustAnchor.key().asLong());

        rtx0(tx -> {
            List<CertificateTreeValidationRun> completed = this.getValidationRuns().findAll(tx, CertificateTreeValidationRun.class);
            assertThat(completed).hasSize(1);
            final List<net.ripe.rpki.validator3.storage.data.validation.ValidationCheck> checks = completed.get(0).getValidationChecks();
            assertThat(checks.get(0).getKey()).isEqualTo(ValidationString.VALIDATOR_NO_MANIFEST_REPOSITORY_FAILED);
            assertThat(checks.get(0).getParameters()).isEqualTo(Collections.singletonList(repository.getRrdpNotifyUri()));
        });
    }

    @Test
    public void should_report_proper_error_when_repository_is_available_but_no_manifest() {
        TrustAnchor trustAnchor = wtx(tx -> {
            TrustAnchor ta = factory.createTypicalTa(tx, KEY_PAIR_FACTORY.generate());
            this.getTrustAnchors().add(tx, ta);
            return ta;
        });
        final URI manifestUri = trustAnchor.getCertificate().getManifestUri();

        RpkiRepository repository = wtx(tx -> {
            final Ref<TrustAnchor> trustAnchorRef = this.getTrustAnchors().makeRef(tx, trustAnchor.key());
            RpkiRepository r = this.getRpkiRepositories().register(tx, trustAnchorRef, TA_RRDP_NOTIFY_URI, RpkiRepository.Type.RRDP);
            r.setDownloaded();
            this.getRpkiRepositories().update(tx, r);

            final Optional<RpkiObject> mft = this.getRpkiObjects().values(tx)
                    .stream()
                    .filter(o -> this.getRpkiObjects().getLocations(tx, o.key()).contains(manifestUri.toASCIIString()))
                    .findFirst();
            mft.ifPresent(m -> this.getRpkiObjects().delete(tx, m));
            return r;
        });

        subject.validate(trustAnchor.key().asLong());

        rtx0(tx -> {
            List<CertificateTreeValidationRun> completed = this.getValidationRuns().findAll(tx, CertificateTreeValidationRun.class);
            assertThat(completed).hasSize(1);
            final List<ValidationCheck> checks = completed.get(0).getValidationChecks();
            assertThat(checks.get(0).getKey()).isEqualTo(ValidationString.VALIDATOR_NO_LOCAL_MANIFEST_NO_MANIFEST_IN_REPOSITORY);
            assertThat(checks.get(0).getParameters()).isEqualTo(Lists.newArrayList(manifestUri.toString(), repository.getRrdpNotifyUri()));
        });
    }

    @Test
    public void should_report_proper_error_when_repository_is_available_but_manifest_is_invalid() {
        KeyPair childKeyPair = KEY_PAIR_FACTORY.generate();

        final ValidityPeriod mftValidityPeriod = new ValidityPeriod(
            Instant.now().minus(Duration.standardDays(2)),
            Instant.now().minus(Duration.standardDays(1))
        );

        TrustAnchor ta = wtx(tx -> {
            TrustAnchor ta1 = factory.createTrustAnchor(tx, x -> {
                CertificateAuthority child = CertificateAuthority.builder()
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
            this.getTrustAnchors().add(tx, ta1);
            return ta1;
        });


        RpkiRepository repository = wtx(tx -> {
            final Ref<TrustAnchor> trustAnchorRef = this.getTrustAnchors().makeRef(tx, ta.key());
            RpkiRepository r = this.getRpkiRepositories().register(tx, trustAnchorRef, TA_RRDP_NOTIFY_URI, RpkiRepository.Type.RRDP);
            r.setFailed();
            this.getRpkiRepositories().update(tx, r);
            return r;
        });

        subject.validate(ta.key().asLong());

        rtx0(tx -> {
            List<CertificateTreeValidationRun> completed = this.getValidationRuns().findAll(tx, CertificateTreeValidationRun.class);
            assertThat(completed).hasSize(1);
            final List<ValidationCheck> checks = completed.get(0).getValidationChecks();
            assertThat(checks.get(0).getKey()).isEqualTo(ValidationString.VALIDATOR_OLD_LOCAL_MANIFEST_REPOSITORY_FAILED);
            assertThat(checks.get(0).getParameters()).isEqualTo(Collections.singletonList(repository.getRrdpNotifyUri()));
        });
    }

    @Test
    public void should_validate_roa() {
        TrustAnchor ta = wtx(tx -> {

            TrustAnchor ta1 = factory.createTrustAnchor(tx, x -> x.roaPrefixes(Collections.singletonList(
                    RoaPrefix.of(IpRange.prefix(IpAddress.parse("192.168.0.0"), 16), 24, Asn.parse("64512"),
                            DateTime.now().toInstant().getMillis(),
                            DateTime.now().plusYears(1).toInstant().getMillis(),
                            TrustAnchorsFactory.nextSerial())
            )));
            this.getTrustAnchors().add(tx, ta1);
            final Ref<TrustAnchor> trustAnchorRef = this.getTrustAnchors().makeRef(tx, ta1.key());
            RpkiRepository repository = this.getRpkiRepositories().register(tx, trustAnchorRef, TA_RRDP_NOTIFY_URI, RpkiRepository.Type.RRDP);
            validationScheduler.addRrdpRpkiRepository(repository);
            repository.setDownloaded();
            this.getRpkiRepositories().update(tx, repository);
            return ta1;
        });

        subject.validate(ta.key().asLong());

        List<CertificateTreeValidationRun> completed = rtx(tx -> this.getValidationRuns().findAll(tx, CertificateTreeValidationRun.class));
        assertThat(completed).hasSize(1);

        CertificateTreeValidationRun result = completed.get(0);

        List<Pair<CertificateTreeValidationRun, RpkiObject>> validatedRoas = rtx(tx -> this.getValidationRuns()
                .findCurrentlyValidated(tx, RpkiObject.Type.ROA).collect(toList()));
        assertThat(validatedRoas).hasSize(1);
        assertThat(validatedRoas.get(0).getLeft()).isEqualTo(result);
    }

    @Test
    public void should_reject_complete_manifest_when_single_object_fails_validation_with_strict_validation(){

        TrustAnchor ta = wtx(tx -> {
            TrustAnchor ta1 = factory.createTrustAnchor(tx, x -> x.roaPrefixes(Lists.newArrayList(
                    RoaPrefix.of(IpRange.prefix(IpAddress.parse("192.168.0.0"), 24), 24, Asn.parse("64512"),
                            DateTime.now().minusDays(10).getMillis(),
                            DateTime.now().minusDays(2).toInstant().getMillis(),
                            TrustAnchorsFactory.nextSerial()),
                    RoaPrefix.of(IpRange.prefix(IpAddress.parse("192.168.1.0"), 24), 24, Asn.parse("64513"),
                            DateTime.now().toInstant().getMillis(),
                            DateTime.now().plusYears(1).toInstant().getMillis(),
                            TrustAnchorsFactory.nextSerial())
            )));
            this.getTrustAnchors().add(tx, ta1);
            final Ref<TrustAnchor> trustAnchorRef = this.getTrustAnchors().makeRef(tx, ta1.key());
            RpkiRepository repository = this.getRpkiRepositories().register(tx, trustAnchorRef, TA_RRDP_NOTIFY_URI, RpkiRepository.Type.RRDP);
            validationScheduler.addRrdpRpkiRepository(repository);
            repository.setDownloaded();
            this.getRpkiRepositories().update(tx, repository);

            return ta1;
        });

        subject.validate(ta.key().asLong());

        List<CertificateTreeValidationRun> completed = rtx(tx -> this.getValidationRuns().findAll(tx, CertificateTreeValidationRun.class));
        assertThat(completed).hasSize(1);

        ValidationCheck check = completed.get(0).getValidationChecks().get(0);
        assertThat(check.getStatus()).isEqualTo(ValidationCheck.Status.ERROR);
        assertThat(check.getKey()).isEqualTo("cert.not.valid.after");

        List<Pair<CertificateTreeValidationRun, RpkiObject>> validatedRoas = rtx(tx -> this.getValidationRuns()
                .findCurrentlyValidated(tx, RpkiObject.Type.ROA).collect(toList()));
        assertThat(validatedRoas).hasSize(0);
    }
}
