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

import net.ripe.ipresource.Asn;
import net.ripe.ipresource.IpAddress;
import net.ripe.ipresource.IpRange;
import net.ripe.ipresource.IpResourceSet;
import net.ripe.rpki.commons.crypto.ValidityPeriod;
import net.ripe.rpki.commons.crypto.x509cert.X509RouterCertificate;
import net.ripe.rpki.commons.validation.ValidationResult;
import net.ripe.rpki.commons.validation.ValidationString;
import net.ripe.rpki.validator3.IntegrationTest;
import net.ripe.rpki.validator3.background.ValidationScheduler;
import net.ripe.rpki.validator3.domain.RoaPrefix;
import net.ripe.rpki.validator3.domain.Settings;
import net.ripe.rpki.validator3.domain.ta.TrustAnchorsFactory;
import net.ripe.rpki.validator3.storage.data.Ref;
import net.ripe.rpki.validator3.storage.data.RpkiObject;
import net.ripe.rpki.validator3.storage.data.RpkiRepository;
import net.ripe.rpki.validator3.storage.data.TrustAnchor;
import net.ripe.rpki.validator3.storage.data.validation.CertificateTreeValidationRun;
import net.ripe.rpki.validator3.storage.data.validation.ValidationCheck;
import net.ripe.rpki.validator3.storage.stores.impl.GenericStorageTest;
import org.apache.commons.lang3.tuple.Pair;
import org.joda.time.Duration;
import org.joda.time.Instant;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit4.SpringRunner;

import javax.security.auth.x500.X500Principal;
import javax.transaction.Transactional;
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

@RunWith(SpringRunner.class)
@IntegrationTest
@Transactional
public class CertificateTreeValidationServiceTest extends GenericStorageTest {

    @Autowired
    private TrustAnchorsFactory factory;

    @Autowired
    private CertificateTreeValidationService subject;

    @Autowired
    private ValidationScheduler validationScheduler;

    @Autowired
    private Settings settings;

    @Test
    public void should_register_rpki_repositories() {
        TrustAnchor ta = factory.createRipeNccTrustAnchor();
        wtx(tx -> getTrustAnchorStore().add(tx, ta));

        subject.validate(ta.getId().asLong());

        List<CertificateTreeValidationRun> completed = rtx(tx -> getValidationRunStore().findAll(tx, CertificateTreeValidationRun.class));
        assertThat(completed).hasSize(1);

        CertificateTreeValidationRun result = completed.get(0);
        assertThat(result.getStatus()).isEqualTo(SUCCEEDED);

        rtx0(tx -> assertThat(getRpkiRepositoryStore().findAll(tx, ta.key())).first().extracting(
                RpkiRepository::getStatus,
                RpkiRepository::getLocationUri
        ).containsExactly(
                RpkiRepository.Status.PENDING,
                "https://rrdp.ripe.net/notification.xml"
        ));

        assertThat(ta.isInitialCertificateTreeValidationRunCompleted()).as("trust anchor initial validation run completed").isFalse();
        assertThat(settings.isInitialValidationRunCompleted()).as("validator initial validation run completed").isFalse();
    }

    @Test
    public void should_register_rsync_repositories() {
        TrustAnchor ta = wtx(tx -> {
            TrustAnchor trustAnchor = factory.createTrustAnchor(tx, x -> {
                x.notifyURI(null);
                x.repositoryURI(TA_CA_REPOSITORY_URI);
            });
            getTrustAnchorStore().add(tx, trustAnchor);
            return trustAnchor;
        });

        subject.validate(ta.getId().asLong());

        List<CertificateTreeValidationRun> completed = rtx(tx -> getValidationRunStore().findAll(tx, CertificateTreeValidationRun.class));
        assertThat(completed).hasSize(1);

        CertificateTreeValidationRun result = completed.get(0);
        assertThat(result.getStatus()).isEqualTo(SUCCEEDED);

        rtx0(tx -> assertThat(getRpkiRepositoryStore().findAll(tx, ta.getId())).first().extracting(
                RpkiRepository::getStatus,
                RpkiRepository::getLocationUri
        ).containsExactly(
                RpkiRepository.Status.PENDING,
                TA_CA_REPOSITORY_URI
        ));

        assertThat(ta.isInitialCertificateTreeValidationRunCompleted()).as("trust anchor initial validation run completed").isFalse();
        assertThat(settings.isInitialValidationRunCompleted()).as("validator initial validation run completed").isFalse();
    }

    @Test
    @Ignore("Fix it --- if fails if TrustAnchorControllerTest is not run before it")
    public void should_validate_minimal_trust_anchor() {
        TrustAnchor ta = wtx(tx -> {
            TrustAnchor trustAnchor = factory.createTrustAnchor(tx, x -> {
                x.notifyURI(null);
                x.repositoryURI(TA_CA_REPOSITORY_URI);
            });
            final Ref<TrustAnchor> trustAnchorRef = getTrustAnchorStore().makeRef(tx, trustAnchor.key());
            RpkiRepository repository = getRpkiRepositoryStore().register(tx, trustAnchorRef, TA_RRDP_NOTIFY_URI, RpkiRepository.Type.RRDP);
            repository.setDownloaded();
            getTrustAnchorStore().add(tx, trustAnchor);
            subject.validate(trustAnchor.getId().asLong());
            return trustAnchor;
        });

        List<CertificateTreeValidationRun> completed = rtx(tx -> getValidationRunStore().findAll(tx, CertificateTreeValidationRun.class));
        assertThat(completed).hasSize(1);

        CertificateTreeValidationRun result = completed.get(0);
        assertThat(result.getValidationChecks()).isEmpty();
        assertThat(result.getStatus()).isEqualTo(SUCCEEDED);

        Set<RpkiObject> validatedObjects = rtx(tx ->
                getValidationRunStore().findAssociatedObjects(tx, result)
                        .stream()
                        .map(pk -> getRpkiObjectStore().get(tx, pk))
                        .filter(Optional::isPresent)
                        .map(Optional::get)
                        .collect(Collectors.toSet()));

        assertThat(validatedObjects)
            .extracting((x) -> x.getLocations().first()).containsExactlyInAnyOrder(
            "rsync://rpki.test/test-trust-anchor.mft",
            "rsync://rpki.test/test-trust-anchor.crl"
        );


        assertThat(ta.isInitialCertificateTreeValidationRunCompleted()).as("trust anchor initial validation run completed").isTrue();
        assertThat(settings.isInitialValidationRunCompleted()).as("validator initial validation run completed").isFalse();
    }

    @Test
    @Ignore("Fix it --- if fails if TrustAnchorControllerTest is not run before it")
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
            getTrustAnchorStore().add(tx, ta);
            final Ref<TrustAnchor> trustAnchorRef = getTrustAnchorStore().makeRef(tx, ta.key());
            RpkiRepository repository = getRpkiRepositoryStore().register(tx, trustAnchorRef, TA_RRDP_NOTIFY_URI, RpkiRepository.Type.RRDP);
            repository.setDownloaded();
            getRpkiRepositoryStore().update(tx, repository);
        });

        subject.validate(ta.getId().asLong());

        List<CertificateTreeValidationRun> completed = rtx(tx -> getValidationRunStore().findAll(tx, CertificateTreeValidationRun.class));
        assertThat(completed).hasSize(1);

        assertThat(ta.isInitialCertificateTreeValidationRunCompleted()).as("trust anchor initial validation run completed").isTrue();
        assertThat(settings.isInitialValidationRunCompleted()).as("validator initial validation run completed").isFalse();

        List<Pair<CertificateTreeValidationRun, RpkiObject>> validated = rtx(tx ->
                getValidationRunStore().findCurrentlyValidated(tx, RpkiObject.Type.CER).collect(toList()));
        assertThat(validated).hasSize(1);
        assertThat(validated.get(0).getLeft()).isEqualTo(completed.get(0));
        Optional<X509RouterCertificate> cro = rtx(tx -> getRpkiObjectStore().findCertificateRepositoryObject(tx,
                validated.get(0).getRight().getId(), X509RouterCertificate.class, ValidationResult.withLocation("ignored.cer")));
        assertThat(cro).isPresent().hasValueSatisfying(x -> assertThat(x.getSubject()).isEqualTo(new X500Principal("CN=child-ca")));
    }

    @Test
    public void should_report_proper_error_when_repository_is_unavailable() {
        TrustAnchor trustAnchor = wtx(tx -> {
            TrustAnchor ta = factory.createTypicalTa(tx, KEY_PAIR_FACTORY.generate());
            getTrustAnchorStore().add(tx, ta);
            return ta;
        });

        RpkiRepository repository = wtx(tx -> {
            final Ref<TrustAnchor> trustAnchorRef = getTrustAnchorStore().makeRef(tx, trustAnchor.key());
            RpkiRepository r = getRpkiRepositoryStore().register(tx, trustAnchorRef, TA_RRDP_NOTIFY_URI, RpkiRepository.Type.RRDP);
            r.setFailed();
            getRpkiRepositoryStore().update(tx, r);

            final URI manifestUri = trustAnchor.getCertificate().getManifestUri();
            final Optional<RpkiObject> mft = getRpkiObjectStore().values(tx).stream().filter(o -> o.getLocations().contains(manifestUri.toASCIIString())).findFirst();
            mft.ifPresent(m -> getRpkiObjectStore().remove(tx, m));
            return r;
        });

        subject.validate(trustAnchor.getId().asLong());

        rtx0(tx -> {
            List<CertificateTreeValidationRun> completed = getValidationRunStore().findAll(tx, CertificateTreeValidationRun.class);
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
            getTrustAnchorStore().add(tx, ta);
            return ta;
        });

        RpkiRepository repository = wtx(tx -> {
            final Ref<TrustAnchor> trustAnchorRef = getTrustAnchorStore().makeRef(tx, trustAnchor.key());
            RpkiRepository r = getRpkiRepositoryStore().register(tx, trustAnchorRef, TA_RRDP_NOTIFY_URI, RpkiRepository.Type.RRDP);
            r.setDownloaded();
            getRpkiRepositoryStore().update(tx, r);

            final URI manifestUri = trustAnchor.getCertificate().getManifestUri();
            final Optional<RpkiObject> mft = getRpkiObjectStore().values(tx).stream()
                    .filter(o -> o.getLocations().contains(manifestUri.toASCIIString())).findFirst();
            mft.ifPresent(m -> getRpkiObjectStore().remove(tx, m));
            return r;
        });

        subject.validate(trustAnchor.getId().asLong());

        rtx0(tx -> {
            List<CertificateTreeValidationRun> completed = getValidationRunStore().findAll(tx, CertificateTreeValidationRun.class);
            assertThat(completed).hasSize(1);
            final List<ValidationCheck> checks = completed.get(0).getValidationChecks();
            assertThat(checks.get(0).getKey()).isEqualTo(ValidationString.VALIDATOR_NO_LOCAL_MANIFEST_NO_MANIFEST_IN_REPOSITORY);
            assertThat(checks.get(0).getParameters()).isEqualTo(Collections.singletonList(repository.getRrdpNotifyUri()));
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
            getTrustAnchorStore().add(tx, ta1);
            return ta1;
        });


        RpkiRepository repository = wtx(tx -> {
            final Ref<TrustAnchor> trustAnchorRef = getTrustAnchorStore().makeRef(tx, ta.key());
            RpkiRepository r = getRpkiRepositoryStore().register(tx, trustAnchorRef, TA_RRDP_NOTIFY_URI, RpkiRepository.Type.RRDP);
            r.setFailed();
            getRpkiRepositoryStore().update(tx, r);
            return r;
        });

        subject.validate(ta.getId().asLong());

        rtx0(tx -> {
            List<CertificateTreeValidationRun> completed = getValidationRunStore().findAll(tx, CertificateTreeValidationRun.class);
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
                    RoaPrefix.of(IpRange.prefix(IpAddress.parse("192.168.0.0"), 16), 24, Asn.parse("64512"))
            )));
            getTrustAnchorStore().add(tx, ta1);
            final Ref<TrustAnchor> trustAnchorRef = getTrustAnchorStore().makeRef(tx, ta1.key());
            RpkiRepository repository = getRpkiRepositoryStore().register(tx, trustAnchorRef, TA_RRDP_NOTIFY_URI, RpkiRepository.Type.RRDP);
            validationScheduler.addRpkiRepository(repository);
            repository.setDownloaded();
            getRpkiRepositoryStore().update(tx, repository);
            return ta1;
        });

        subject.validate(ta.getId().asLong());

        List<CertificateTreeValidationRun> completed = rtx(tx -> getValidationRunStore().findAll(tx, CertificateTreeValidationRun.class));
        assertThat(completed).hasSize(1);

        CertificateTreeValidationRun result = completed.get(0);

        List<Pair<CertificateTreeValidationRun, RpkiObject>> validatedRoas = rtx(tx -> getValidationRunStore()
                .findCurrentlyValidated(tx, RpkiObject.Type.ROA).collect(toList()));
        assertThat(validatedRoas).hasSize(1);
        assertThat(validatedRoas.get(0).getLeft()).isEqualTo(result);
        assertThat(validatedRoas.get(0).getRight().getRoaPrefixes()).hasSize(1);
    }
}
