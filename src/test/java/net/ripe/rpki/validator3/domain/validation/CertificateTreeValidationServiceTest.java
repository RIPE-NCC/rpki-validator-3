package net.ripe.rpki.validator3.domain.validation;

import com.google.common.io.Resources;
import lombok.Builder;
import lombok.Value;
import net.ripe.ipresource.*;
import net.ripe.rpki.commons.crypto.ValidityPeriod;
import net.ripe.rpki.commons.crypto.cms.manifest.ManifestCms;
import net.ripe.rpki.commons.crypto.cms.manifest.ManifestCmsBuilder;
import net.ripe.rpki.commons.crypto.cms.roa.RoaCms;
import net.ripe.rpki.commons.crypto.cms.roa.RoaCmsBuilder;
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
import org.apache.commons.lang3.tuple.Pair;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.joda.time.Instant;
import org.junit.BeforeClass;
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
import java.security.PublicKey;
import java.security.Security;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.function.Consumer;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static net.ripe.rpki.validator3.domain.ValidationRun.Status.SUCCEEDED;
import static org.assertj.core.api.Assertions.assertThat;

@RunWith(SpringRunner.class)
@SpringBootTest
@Transactional
public class CertificateTreeValidationServiceTest {

    private static final X509ResourceCertificate RIPE_NCC_TA_CERTIFICATE = loadCertificate("/ripe-ncc-ta.cer");
    private static final String RPKI_NOTIFY_URI = "https://rpki.test/notification.xml";
    private static final String TA_REPOSITORY_URI = "rsync://rpki.test/";
    private static final String TA_MANIFEST_URI = "rsync://rpki.test/test-trust-anchor.mft";
    private static final String TA_CRL_URI = "rsync://rpki.test/test-trust-anchor.crl";
    private static final KeyPairFactory KEY_PAIR_FACTORY = new KeyPairFactory(BouncyCastleProvider.PROVIDER_NAME);
    private static final String TA_ISSUER_DN = "CN=test";

    private static X509ResourceCertificate loadCertificate(String name) {
        try {
            byte[] encoded = Resources.toByteArray(CertificateTreeValidationServiceTest.class.getResource(name));
            return (X509ResourceCertificate) CertificateRepositoryObjectFactory.createCertificateRepositoryObject(encoded, ValidationResult.withLocation(name));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static BigInteger nextSerial = BigInteger.ONE;

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

    @BeforeClass
    public static void add_security_provider() {
        Security.addProvider(new BouncyCastleProvider());
    }

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
        TrustAnchor ta = createTrustAnchor(x -> {
        });
        trustAnchors.add(ta);
        RpkiRepository repository = rpkiRepositories.register(ta, RPKI_NOTIFY_URI);
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
    public void should_validate_roa() {
        TrustAnchor ta = createTrustAnchor(x -> {
            x.roaPrefixes(Arrays.asList(
                RoaPrefix.of(IpRange.prefix(IpAddress.parse("192.168.0.0"), 16), 24, Asn.parse("64512"))
            ));
        });
        trustAnchors.add(ta);
        RpkiRepository repository = rpkiRepositories.register(ta, RPKI_NOTIFY_URI);
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

    private TrustAnchor createTrustAnchor(Consumer<CertificateAuthority.CertificateAuthorityBuilder> configure) {
        KeyPair keyPair = KEY_PAIR_FACTORY.generate();
        CertificateAuthority.CertificateAuthorityBuilder builder = CertificateAuthority.builder()
            .name("test trust anchor")
            .certificateLocation("rsync://rpki.test/test-trust-anchor.cer")
            .resources(IpResourceSet.parse("0.0.0.0/0"))
            .issuerDN(TA_ISSUER_DN)
            .subjectDN(TA_ISSUER_DN)
            .publicKey(keyPair.getPublic())
            .signingKey(keyPair)
            .notifyURI(RPKI_NOTIFY_URI)
            .manifestURI(TA_MANIFEST_URI)
            .repositoryURI(TA_REPOSITORY_URI)
            .crlDistributionPoint(TA_CRL_URI);
        configure.accept(builder);
        CertificateAuthority root = builder.build();

        X509ResourceCertificate certificate = createCertificateAuthority(root);

        TrustAnchor ta = new TrustAnchor();
        ta.setName(root.name);
        ta.getLocations().add(root.certificateLocation);
        ta.setCertificate(certificate);
        ta.setSubjectPublicKeyInfo(X509CertificateUtil.getEncodedSubjectPublicKeyInfo(certificate.getCertificate()));
        return ta;
    }

    private X509ResourceCertificate createCertificateAuthority(CertificateAuthority root) {
        X509ResourceCertificate certificate = new X509ResourceCertificateBuilder()
            .withResources(root.resources)
            .withIssuerDN(new X500Principal(root.issuerDN))
            .withSubjectDN(new X500Principal(root.subjectDN))
            .withSubjectInformationAccess(
                new X509CertificateInformationAccessDescriptor(X509CertificateInformationAccessDescriptor.ID_AD_RPKI_NOTIFY, URI.create(root.notifyURI)),
                new X509CertificateInformationAccessDescriptor(X509CertificateInformationAccessDescriptor.ID_AD_RPKI_MANIFEST, URI.create(root.manifestURI))
            )
            .withCa(true)
            .withKeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign)
            .withSerial(nextSerial())
            .withValidityPeriod(new ValidityPeriod(Instant.now(), Instant.now().plus(Duration.standardDays(7))))
            .withSubjectKeyIdentifier(true)
            .withPublicKey(root.publicKey)
            .withSigningKeyPair(root.signingKey)
            .build();

        X509Crl crl = new X509CrlBuilder()
            .withIssuerDN(new X500Principal("CN=test"))
            .withThisUpdateTime(DateTime.now())
            .withNextUpdateTime(DateTime.now().plusHours(8))
            .withAuthorityKeyIdentifier(root.getPublicKey())
            .withNumber(nextSerial())
            .build(root.signingKey.getPrivate());
        rpkiObjects.add(new RpkiObject(TA_CRL_URI, crl));

        ManifestCmsBuilder manifestBuilder = new ManifestCmsBuilder();
        manifestBuilder.addFile("test-trust-anchor.crl", crl.getEncoded());

        if (root.roaPrefixes != null) {
            root.roaPrefixes.stream().collect(groupingBy(RoaPrefix::getAsn)).forEach((asn, roaPrefix) -> {
                KeyPair roaKeyPair = KEY_PAIR_FACTORY.generate();
                IpResourceSet resources = new IpResourceSet();
                roaPrefix.stream().forEach(p -> resources.add(IpRange.parse(p.getPrefix())));
                X509ResourceCertificate roaCertificate = new X509ResourceCertificateBuilder()
//                    .withInheritedResourceTypes(EnumSet.allOf(IpResourceType.class))
                    .withResources(resources)
                    .withIssuerDN(new X500Principal(root.subjectDN))
                    .withSubjectDN(new X500Principal("CN=AS" + asn + ", CN=roa, " + root.subjectDN))
                    .withValidityPeriod(new ValidityPeriod(Instant.now(), Instant.now().plus(Duration.standardDays(1))))
                    .withPublicKey(roaKeyPair.getPublic())
                    .withSigningKeyPair(root.signingKey)
                    .withCa(false)
                    .withKeyUsage(KeyUsage.digitalSignature)
                    .withSerial(nextSerial())
                    .withCrlDistributionPoints(URI.create(root.crlDistributionPoint))
                    .build();
                RoaCms roaCms = new RoaCmsBuilder()
                    .withAsn(new Asn(asn))
                    .withPrefixes(roaPrefix.stream()
                        .map(p -> new net.ripe.rpki.commons.crypto.cms.roa.RoaPrefix(IpRange.parse(p.getPrefix()), p.getMaximumLength()))
                        .collect(toList()))
                    .withCertificate(roaCertificate)
                    .withSignatureProvider(BouncyCastleProvider.PROVIDER_NAME)
                    .build(roaKeyPair.getPrivate());
                rpkiObjects.add(new RpkiObject(root.repositoryURI + "/" + "AS" + asn + ".roa", roaCms));

                manifestBuilder.addFile("AS" + asn + ".roa", roaCms.getEncoded());
            });
        }

        KeyPair manifestKeyPair = KEY_PAIR_FACTORY.generate();
        X509ResourceCertificate manifestCertificate = new X509ResourceCertificateBuilder()
            .withInheritedResourceTypes(EnumSet.allOf(IpResourceType.class))
            .withIssuerDN(new X500Principal(root.subjectDN))
            .withSubjectDN(new X500Principal("CN=manifest, " + root.subjectDN))
            .withValidityPeriod(new ValidityPeriod(Instant.now(), Instant.now().plus(Duration.standardDays(1))))
            .withPublicKey(manifestKeyPair.getPublic())
            .withSigningKeyPair(root.signingKey)
            .withCa(false)
            .withKeyUsage(KeyUsage.digitalSignature)
            .withSerial(nextSerial())
            .withCrlDistributionPoints(URI.create(root.crlDistributionPoint))
            .build();
        manifestBuilder
            .withCertificate(manifestCertificate)
            .withManifestNumber(nextSerial())
            .withThisUpdateTime(DateTime.now())
            .withNextUpdateTime(DateTime.now().plusHours(8));
        ManifestCms manifest = manifestBuilder.build(manifestKeyPair.getPrivate());
        rpkiObjects.add(new RpkiObject(TA_MANIFEST_URI, manifest));
        return certificate;
    }

    protected TrustAnchor createRipeNccTrustAnchor() {
        TrustAnchor ta = TrustAnchorValidationServiceTest.createRipeNccTrustAnchor();
        ta.setCertificate(RIPE_NCC_TA_CERTIFICATE);
        return ta;
    }

    private static BigInteger nextSerial() {
        BigInteger result = nextSerial;
        nextSerial = nextSerial.add(BigInteger.ONE);
        return result;
    }

    @Value
    @Builder
    public static class CertificateAuthority {
        String name;
        String certificateLocation;
        IpResourceSet resources;
        String issuerDN;
        String subjectDN;
        PublicKey publicKey;
        KeyPair signingKey;
        String notifyURI;
        String manifestURI;
        String repositoryURI;

        String crlDistributionPoint;

        List<RoaPrefix> roaPrefixes;
    }
}
