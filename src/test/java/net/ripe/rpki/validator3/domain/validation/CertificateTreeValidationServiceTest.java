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
import net.ripe.rpki.commons.crypto.x509cert.X509ResourceCertificate;
import net.ripe.rpki.commons.validation.ValidationResult;
import net.ripe.rpki.validator3.IntegrationTest;
import net.ripe.rpki.validator3.domain.CertificateTreeValidationRun;
import net.ripe.rpki.validator3.domain.RoaPrefix;
import net.ripe.rpki.validator3.domain.RpkiObject;
import net.ripe.rpki.validator3.domain.RpkiObjects;
import net.ripe.rpki.validator3.domain.RpkiRepositories;
import net.ripe.rpki.validator3.domain.RpkiRepository;
import net.ripe.rpki.validator3.domain.TrustAnchor;
import net.ripe.rpki.validator3.domain.TrustAnchors;
import net.ripe.rpki.validator3.domain.TrustAnchorsFactory;
import net.ripe.rpki.validator3.domain.ValidationRuns;
import org.apache.commons.lang3.tuple.Pair;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit4.SpringRunner;

import javax.persistence.EntityManager;
import javax.security.auth.x500.X500Principal;
import javax.transaction.Transactional;
import java.security.KeyPair;
import java.security.Security;
import java.util.Arrays;
import java.util.List;

import static java.util.stream.Collectors.toList;
import static net.ripe.rpki.validator3.domain.TrustAnchorsFactory.KEY_PAIR_FACTORY;
import static net.ripe.rpki.validator3.domain.TrustAnchorsFactory.TA_CA_REPOSITORY_URI;
import static net.ripe.rpki.validator3.domain.TrustAnchorsFactory.TA_RRDP_NOTIFY_URI;
import static net.ripe.rpki.validator3.domain.ValidationRun.Status.SUCCEEDED;
import static org.assertj.core.api.Assertions.assertThat;

@RunWith(SpringRunner.class)
@IntegrationTest
@Transactional
public class CertificateTreeValidationServiceTest {

    @Autowired
    private TrustAnchorsFactory factory;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TrustAnchors trustAnchors;

    @Autowired
    private CertificateTreeValidationService subject;

    @Autowired
    private ValidationRuns validationRuns;

    @Autowired
    private RpkiRepositories rpkiRepositories;

    @Autowired
    private RpkiObjects rpkiObjects;

    @Test
    public void should_register_rpki_repositories() {
        TrustAnchor ta = factory.createRipeNccTrustAnchor();
        trustAnchors.add(ta);

        subject.validate(ta.getId());
        entityManager.flush();

        List<CertificateTreeValidationRun> completed = validationRuns.findAll(CertificateTreeValidationRun.class);
        assertThat(completed).hasSize(1);

        CertificateTreeValidationRun result = completed.get(0);
        assertThat(result.getStatus()).isEqualTo(SUCCEEDED);

        assertThat(rpkiRepositories.findAll(null)).first().extracting(
            RpkiRepository::getStatus,
            RpkiRepository::getLocationUri
        ).containsExactly(
            RpkiRepository.Status.PENDING,
            "https://rrdp.ripe.net/notification.xml"
        );
    }

    @Test
    public void should_register_rsync_repositories() {
        TrustAnchor ta = factory.createTrustAnchor(x -> {
            x.notifyURI(null);
            x.repositoryURI(TA_CA_REPOSITORY_URI);
        });
        trustAnchors.add(ta);

        subject.validate(ta.getId());
        entityManager.flush();

        List<CertificateTreeValidationRun> completed = validationRuns.findAll(CertificateTreeValidationRun.class);
        assertThat(completed).hasSize(1);

        CertificateTreeValidationRun result = completed.get(0);
        assertThat(result.getStatus()).isEqualTo(SUCCEEDED);

        assertThat(rpkiRepositories.findAll(null)).first().extracting(
            RpkiRepository::getStatus,
            RpkiRepository::getLocationUri
        ).containsExactly(
            RpkiRepository.Status.PENDING,
            TA_CA_REPOSITORY_URI
        );

    }

    @Test
    public void should_validate_minimal_trust_anchor() {
        TrustAnchor ta = factory.createTrustAnchor(x -> {
        });
        trustAnchors.add(ta);
        RpkiRepository repository = rpkiRepositories.register(ta, TA_RRDP_NOTIFY_URI, RpkiRepository.Type.RRDP);
        repository.setDownloaded();
        entityManager.flush();

        subject.validate(ta.getId());
        entityManager.flush();

        List<CertificateTreeValidationRun> completed = validationRuns.findAll(CertificateTreeValidationRun.class);
        assertThat(completed).hasSize(1);

        CertificateTreeValidationRun result = completed.get(0);
        assertThat(result.getValidationChecks()).isEmpty();
        assertThat(result.getStatus()).isEqualTo(SUCCEEDED);

        assertThat(result.getValidatedObjects())
            .extracting((x) -> x.getLocations().first()).containsExactlyInAnyOrder(
            "rsync://rpki.test/test-trust-anchor.mft",
            "rsync://rpki.test/test-trust-anchor.crl"
        );
    }

    @Test
    public void should_validate_child_ca() {
        KeyPair childKeyPair = KEY_PAIR_FACTORY.generate();

        TrustAnchor ta = factory.createTrustAnchor(x -> {
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
        });
        trustAnchors.add(ta);
        RpkiRepository repository = rpkiRepositories.register(ta, TA_RRDP_NOTIFY_URI, RpkiRepository.Type.RRDP);
        repository.setDownloaded();
        entityManager.flush();

        subject.validate(ta.getId());
        entityManager.flush();

        List<CertificateTreeValidationRun> completed = validationRuns.findAll(CertificateTreeValidationRun.class);
        assertThat(completed).hasSize(1);

        List<Pair<CertificateTreeValidationRun, RpkiObject>> validated = rpkiObjects.findCurrentlyValidated(RpkiObject.Type.CER).collect(toList());
        assertThat(validated).hasSize(1);
        assertThat(validated.get(0).getLeft()).isEqualTo(completed.get(0));
        assertThat(validated.get(0).getRight().get(X509ResourceCertificate.class, ValidationResult.withLocation("ignored.cer")).get().getSubject()).isEqualTo(new X500Principal("CN=child-ca"));
    }

    @Test
    public void should_validate_roa() {
        TrustAnchor ta = factory.createTrustAnchor(x -> {
            x.roaPrefixes(Arrays.asList(
                RoaPrefix.of(IpRange.prefix(IpAddress.parse("192.168.0.0"), 16), 24, Asn.parse("64512"))
            ));
        });
        trustAnchors.add(ta);
        RpkiRepository repository = rpkiRepositories.register(ta, TA_RRDP_NOTIFY_URI, RpkiRepository.Type.RRDP);
        repository.setDownloaded();
        entityManager.flush();

        subject.validate(ta.getId());
        entityManager.flush();

        List<CertificateTreeValidationRun> completed = validationRuns.findAll(CertificateTreeValidationRun.class);
        assertThat(completed).hasSize(1);

        CertificateTreeValidationRun result = completed.get(0);

        List<Pair<CertificateTreeValidationRun, RpkiObject>> validatedRoas = rpkiObjects.findCurrentlyValidated(RpkiObject.Type.ROA).collect(toList());
        assertThat(validatedRoas).hasSize(1);
        assertThat(validatedRoas.get(0).getLeft()).isEqualTo(result);
        assertThat(validatedRoas.get(0).getRight().getRoaPrefixes()).hasSize(1);
    }

}
