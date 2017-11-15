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
import java.util.ArrayList;
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
    private static final String TA_RRDP_NOTIFY_URI = "https://rpki.test/notification.xml";
    private static final String TA_CA_REPOSITORY_URI = "rsync://rpki.test/repository";
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
    public void should_register_rpki_repositories() {
        TrustAnchor ta = createRipeNccTrustAnchor();
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
        TrustAnchor ta = createTrustAnchor(x -> {
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
        TrustAnchor ta = createTrustAnchor(x -> {
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

        TrustAnchor ta = createTrustAnchor(x -> {
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
        TrustAnchor ta = createTrustAnchor(x -> {
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

    private TrustAnchor createTrustAnchor(Consumer<CertificateAuthority.CertificateAuthorityBuilder> configure) {
        KeyPair rootKeyPair = KEY_PAIR_FACTORY.generate();
        CertificateAuthority.CertificateAuthorityBuilder builder = CertificateAuthority.builder()
            .dn("CN=test-trust-anchor")
            .keyPair(rootKeyPair)
            .certificateLocation("rsync://rpki.test/test-trust-anchor.cer")
            .resources(IpResourceSet.parse("0.0.0.0/0"))
            .notifyURI(TA_RRDP_NOTIFY_URI)
            .manifestURI(TA_MANIFEST_URI)
            .repositoryURI(TA_CA_REPOSITORY_URI)
            .crlDistributionPoint(TA_CRL_URI);
        configure.accept(builder);
        CertificateAuthority root = builder.build();

        X509ResourceCertificate certificate = createCertificateAuthority(root, root);

        TrustAnchor ta = new TrustAnchor();
        ta.setName(root.dn);
        ta.getLocations().add(root.certificateLocation);
        ta.setCertificate(certificate);
        ta.setSubjectPublicKeyInfo(X509CertificateUtil.getEncodedSubjectPublicKeyInfo(certificate.getCertificate()));
        return ta;
    }

    private X509ResourceCertificate createCertificateAuthority(CertificateAuthority ca, CertificateAuthority issuer) {
        ManifestCmsBuilder manifestBuilder = new ManifestCmsBuilder();

        X509ResourceCertificate caCertificate = createCaCertificate(ca, ca.keyPair.getPublic(), issuer.dn, issuer.crlDistributionPoint, issuer.keyPair);

        X509Crl crl = new X509CrlBuilder()
            .withIssuerDN(caCertificate.getSubject())
            .withThisUpdateTime(DateTime.now())
            .withNextUpdateTime(DateTime.now().plusHours(8))
            .withAuthorityKeyIdentifier(ca.keyPair.getPublic())
            .withNumber(nextSerial())
            .build(ca.keyPair.getPrivate());

        rpkiObjects.add(new RpkiObject(ca.crlDistributionPoint, crl));
        manifestBuilder.addFile(ca.crlDistributionPoint.substring(ca.crlDistributionPoint.lastIndexOf('/') + 1), crl.getEncoded());

        if (ca.children != null) {
            for (CertificateAuthority child : ca.children) {
                X509ResourceCertificate childCertificate = createCertificateAuthority(child, ca);

                rpkiObjects.add(new RpkiObject(ca.repositoryURI + "/" + child.dn + ".cer", childCertificate));
                manifestBuilder.addFile(child.dn + ".cer", childCertificate.getEncoded());
            }
        }

        if (ca.roaPrefixes != null) {
            ca.roaPrefixes.stream().collect(groupingBy(RoaPrefix::getAsn)).forEach((asn, roaPrefix) -> {
                KeyPair roaKeyPair = KEY_PAIR_FACTORY.generate();
                IpResourceSet resources = new IpResourceSet();
                roaPrefix.stream().forEach(p -> resources.add(IpRange.parse(p.getPrefix())));
                X509ResourceCertificate roaCertificate = new X509ResourceCertificateBuilder()
//                    .withInheritedResourceTypes(EnumSet.allOf(IpResourceType.class))
                    .withResources(resources)
                    .withIssuerDN(new X500Principal(ca.dn))
                    .withSubjectDN(new X500Principal("CN=AS" + asn + ", CN=roa, " + ca.dn))
                    .withValidityPeriod(new ValidityPeriod(Instant.now(), Instant.now().plus(Duration.standardDays(1))))
                    .withPublicKey(roaKeyPair.getPublic())
                    .withSigningKeyPair(ca.keyPair)
                    .withCa(false)
                    .withKeyUsage(KeyUsage.digitalSignature)
                    .withSerial(nextSerial())
                    .withCrlDistributionPoints(URI.create(ca.crlDistributionPoint))
                    .build();
                RoaCms roaCms = new RoaCmsBuilder()
                    .withAsn(new Asn(asn))
                    .withPrefixes(roaPrefix.stream()
                        .map(p -> new net.ripe.rpki.commons.crypto.cms.roa.RoaPrefix(IpRange.parse(p.getPrefix()), p.getMaximumLength()))
                        .collect(toList()))
                    .withCertificate(roaCertificate)
                    .withSignatureProvider(BouncyCastleProvider.PROVIDER_NAME)
                    .build(roaKeyPair.getPrivate());

                rpkiObjects.add(new RpkiObject(ca.repositoryURI + "/" + "AS" + asn + ".roa", roaCms));
                manifestBuilder.addFile("AS" + asn + ".roa", roaCms.getEncoded());
            });
        }

        KeyPair manifestKeyPair = KEY_PAIR_FACTORY.generate();
        X509ResourceCertificate manifestCertificate = new X509ResourceCertificateBuilder()
            .withInheritedResourceTypes(EnumSet.allOf(IpResourceType.class))
            .withIssuerDN(caCertificate.getSubject())
            .withSubjectDN(new X500Principal("CN=manifest, " + caCertificate.getSubject()))
            .withValidityPeriod(new ValidityPeriod(Instant.now(), Instant.now().plus(Duration.standardDays(1))))
            .withPublicKey(manifestKeyPair.getPublic())
            .withSigningKeyPair(ca.keyPair)
            .withCa(false)
            .withKeyUsage(KeyUsage.digitalSignature)
            .withSerial(nextSerial())
            .withCrlDistributionPoints(URI.create(ca.crlDistributionPoint))
            .build();
        manifestBuilder
            .withCertificate(manifestCertificate)
            .withManifestNumber(nextSerial())
            .withThisUpdateTime(DateTime.now())
            .withNextUpdateTime(DateTime.now().plusHours(8));
        ManifestCms manifest = manifestBuilder.build(manifestKeyPair.getPrivate());
        rpkiObjects.add(new RpkiObject(ca.manifestURI, manifest));
        return caCertificate;
    }

    private X509ResourceCertificate createCaCertificate(CertificateAuthority ca, PublicKey publicKey, String issuerDN, String crlDistributionPoint, KeyPair signingKey) {
        List<X509CertificateInformationAccessDescriptor> sia = new ArrayList<>();
        sia.add(new X509CertificateInformationAccessDescriptor(X509CertificateInformationAccessDescriptor.ID_AD_RPKI_MANIFEST, URI.create(ca.manifestURI)));
        sia.add(new X509CertificateInformationAccessDescriptor(X509CertificateInformationAccessDescriptor.ID_AD_CA_REPOSITORY, URI.create(ca.repositoryURI)));
        if (ca.notifyURI != null) {
            sia.add(new X509CertificateInformationAccessDescriptor(X509CertificateInformationAccessDescriptor.ID_AD_RPKI_NOTIFY, URI.create(ca.notifyURI)));
        }

        return new X509ResourceCertificateBuilder()
            .withResources(ca.resources)
            .withIssuerDN(new X500Principal(issuerDN))
            .withSubjectDN(new X500Principal(ca.dn))
            .withSubjectInformationAccess(sia.toArray(new X509CertificateInformationAccessDescriptor[0]))
            .withCrlDistributionPoints(URI.create(crlDistributionPoint))
            .withCa(true)
            .withKeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign)
            .withSerial(nextSerial())
            .withValidityPeriod(new ValidityPeriod(Instant.now(), Instant.now().plus(Duration.standardDays(7))))
            .withSubjectKeyIdentifier(true)
            .withPublicKey(publicKey)
            .withSigningKeyPair(signingKey)
            .build();
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
        String dn;
        KeyPair keyPair;
        String certificateLocation;
        IpResourceSet resources;
        String notifyURI;
        String manifestURI;
        String repositoryURI;

        String crlDistributionPoint;

        List<CertificateAuthority> children;
        List<RoaPrefix> roaPrefixes;
    }
}
