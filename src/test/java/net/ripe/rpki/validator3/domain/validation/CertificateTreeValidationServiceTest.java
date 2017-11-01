package net.ripe.rpki.validator3.domain.validation;

import com.google.common.io.Resources;
import net.ripe.ipresource.IpResourceSet;
import net.ripe.ipresource.IpResourceType;
import net.ripe.rpki.commons.crypto.ValidityPeriod;
import net.ripe.rpki.commons.crypto.cms.manifest.ManifestCms;
import net.ripe.rpki.commons.crypto.cms.manifest.ManifestCmsBuilder;
import net.ripe.rpki.commons.crypto.crl.X509Crl;
import net.ripe.rpki.commons.crypto.crl.X509CrlBuilder;
import net.ripe.rpki.commons.crypto.util.CertificateRepositoryObjectFactory;
import net.ripe.rpki.commons.crypto.util.KeyPairFactory;
import net.ripe.rpki.commons.crypto.x509cert.X509CertificateInformationAccessDescriptor;
import net.ripe.rpki.commons.crypto.x509cert.X509CertificateUtil;
import net.ripe.rpki.commons.crypto.x509cert.X509ResourceCertificate;
import net.ripe.rpki.commons.crypto.x509cert.X509ResourceCertificateBuilder;
import net.ripe.rpki.commons.validation.ValidationResult;
import net.ripe.rpki.validator3.domain.*;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.joda.time.Instant;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import javax.persistence.EntityManager;
import javax.security.auth.x500.X500Principal;
import javax.transaction.Transactional;
import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.security.KeyPair;
import java.security.Security;
import java.util.EnumSet;
import java.util.List;

import static net.ripe.rpki.validator3.domain.ValidationRun.Status.SUCCEEDED;
import static org.assertj.core.api.Assertions.assertThat;

@RunWith(SpringRunner.class)
@SpringBootTest
@Transactional
public class CertificateTreeValidationServiceTest {

    private static final X509ResourceCertificate RIPE_NCC_TA_CERTIFICATE = loadCertificate("/ripe-ncc-ta.cer");
    private static final URI RPKI_NOTIFY_URI = URI.create("https://rpki.test/notification.xml");
    private static final URI TA_MANIFEST_URI = URI.create("rsync://rpki.test/test-trust-anchor.mft");
    private static final URI TA_CRL_URI = URI.create("rsync://rpki.test/test-trust-anchor.crl");
    private static final KeyPairFactory KEY_PAIR_FACTORY = new KeyPairFactory(BouncyCastleProvider.PROVIDER_NAME);
    private static final X500Principal TA_ISSUER_DN = new X500Principal("CN=test");

    private static X509ResourceCertificate loadCertificate(String name) {
        try {
            byte[] encoded = Resources.toByteArray(CertificateTreeValidationServiceTest.class.getResource(name));
            return (X509ResourceCertificate) CertificateRepositoryObjectFactory.createCertificateRepositoryObject(encoded, ValidationResult.withLocation(name));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

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
    public void should_register_newly_discovered_repositories() {
        TrustAnchor ta = createRipeNccTrustAnchor();
        trustAnchors.add(ta);

        subject.validate(ta.getId());
        entityManager.flush();

        List<CertificateTreeValidationRun> completed = validationRuns.findAll(CertificateTreeValidationRun.class);
        assertThat(completed).hasSize(1);

        CertificateTreeValidationRun result = completed.get(0);
        assertThat(result.getStatus()).isEqualTo(SUCCEEDED);

        assertThat(rpkiRepositories.findAll())
            .first().extracting(RpkiRepository::getStatus).containsExactly(RpkiRepository.Status.PENDING);

    }

    @Test
    public void should_validate_minimal_trust_anchor() {
        TrustAnchor ta = createTestTrustAnchor();
        trustAnchors.add(ta);
        RpkiRepository repository = rpkiRepositories.register(ta, RPKI_NOTIFY_URI.toASCIIString());
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

    private TrustAnchor createTestTrustAnchor() {
        TrustAnchor ta = new TrustAnchor();
        ta.setName("test trust anchor");
        ta.getLocations().add("rsync://rpki.test/test-trust-anchor.cer");
        Security.addProvider(new BouncyCastleProvider());
        KeyPair keyPair = KEY_PAIR_FACTORY.generate();
        X509ResourceCertificate certificate = new X509ResourceCertificateBuilder()
            .withResources(IpResourceSet.parse("0.0.0.0/0"))
            .withIssuerDN(TA_ISSUER_DN)
            .withSubjectDN(TA_ISSUER_DN)
            .withSubjectInformationAccess(
                new X509CertificateInformationAccessDescriptor(X509CertificateInformationAccessDescriptor.ID_AD_RPKI_NOTIFY, RPKI_NOTIFY_URI),
                new X509CertificateInformationAccessDescriptor(X509CertificateInformationAccessDescriptor.ID_AD_RPKI_MANIFEST, TA_MANIFEST_URI)
            )
            .withCa(true)
            .withKeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign)
            .withSerial(BigInteger.ONE)
            .withValidityPeriod(new ValidityPeriod(Instant.now(), Instant.now().plus(Duration.standardDays(7))))
            .withSubjectKeyIdentifier(true)
            .withPublicKey(keyPair.getPublic())
            .withSigningKeyPair(keyPair)
            .build();
        ta.setCertificate(certificate);
        ta.setSubjectPublicKeyInfo(X509CertificateUtil.getEncodedSubjectPublicKeyInfo(certificate.getCertificate()));

        X509Crl crl = new X509CrlBuilder()
            .withIssuerDN(new X500Principal("CN=test"))
            .withThisUpdateTime(DateTime.now())
            .withNextUpdateTime(DateTime.now().plusHours(8))
            .withAuthorityKeyIdentifier(keyPair.getPublic())
            .withNumber(BigInteger.ONE)
            .build(keyPair.getPrivate());
        rpkiObjects.add(new RpkiObject(TA_CRL_URI, crl));

        KeyPair manifestKeyPair = KEY_PAIR_FACTORY.generate();
        X509ResourceCertificate manifestCertificate = new X509ResourceCertificateBuilder()
            .withInheritedResourceTypes(EnumSet.allOf(IpResourceType.class))
            .withIssuerDN(TA_ISSUER_DN)
            .withSubjectDN(new X500Principal("CN=manifest, CN=test"))
            .withValidityPeriod(new ValidityPeriod(Instant.now(), Instant.now().plus(Duration.standardDays(1))))
            .withPublicKey(manifestKeyPair.getPublic())
            .withSigningKeyPair(keyPair)
            .withCa(false)
            .withKeyUsage(KeyUsage.digitalSignature)
            .withSerial(BigInteger.valueOf(2))
            .withCrlDistributionPoints(TA_CRL_URI)
            .build();
        ManifestCmsBuilder manifestBuilder = new ManifestCmsBuilder();
        manifestBuilder
            .withCertificate(manifestCertificate)
            .withManifestNumber(BigInteger.ONE)
            .withThisUpdateTime(DateTime.now())
            .withNextUpdateTime(DateTime.now().plusHours(8));
        manifestBuilder.addFile("test-trust-anchor.crl", crl.getEncoded());
        ManifestCms manifest = manifestBuilder.build(manifestKeyPair.getPrivate());
        rpkiObjects.add(new RpkiObject(TA_MANIFEST_URI, manifest));
        return ta;
    }

    protected TrustAnchor createRipeNccTrustAnchor() {
        TrustAnchor ta = TrustAnchorValidationServiceTest.createRipeNccTrustAnchor();
        ta.setCertificate(RIPE_NCC_TA_CERTIFICATE);
        return ta;
    }
}
