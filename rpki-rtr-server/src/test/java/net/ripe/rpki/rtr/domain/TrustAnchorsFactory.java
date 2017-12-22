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
package net.ripe.rpki.rtr.domain;

import com.google.common.io.Resources;
import lombok.Builder;
import lombok.Value;
import net.ripe.ipresource.Asn;
import net.ripe.ipresource.IpRange;
import net.ripe.ipresource.IpResourceSet;
import net.ripe.ipresource.IpResourceType;
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
import net.ripe.rpki.rtr.domain.validation.CertificateTreeValidationServiceTest;
import net.ripe.rpki.rtr.domain.validation.TrustAnchorValidationServiceTest;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.joda.time.Instant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.security.auth.x500.X500Principal;
import javax.transaction.Transactional;
import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.security.KeyPair;
import java.security.PublicKey;
import java.security.Security;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.function.Consumer;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;

@Component
@Transactional
public class TrustAnchorsFactory {
    private static final X509ResourceCertificate RIPE_NCC_TA_CERTIFICATE = loadCertificate("/ripe-ncc-ta.cer");
    public static final String TA_RRDP_NOTIFY_URI = "https://rpki.test/notification.xml";
    public static final String TA_CA_REPOSITORY_URI = "rsync://rpki.test/repository";
    private static final String TA_MANIFEST_URI = "rsync://rpki.test/test-trust-anchor.mft";
    private static final String TA_CRL_URI = "rsync://rpki.test/test-trust-anchor.crl";
    public static final KeyPairFactory KEY_PAIR_FACTORY = new KeyPairFactory(BouncyCastleProvider.PROVIDER_NAME);
    private static final String TA_ISSUER_DN = "CN=test";

    private static X509ResourceCertificate loadCertificate(String name) {
        try {
            byte[] encoded = Resources.toByteArray(CertificateTreeValidationServiceTest.class.getResource(name));
            return (X509ResourceCertificate) CertificateRepositoryObjectFactory.createCertificateRepositoryObject(encoded, ValidationResult.withLocation(name));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Autowired
    private RpkiObjects rpkiObjects;

    private static BigInteger nextSerial = BigInteger.ONE;

    @PostConstruct
    public void add_security_provider() {
        Security.addProvider(new BouncyCastleProvider());
    }

    public ValidityPeriod typicalValidityPeriod() {
        return new ValidityPeriod(Instant.now(), Instant.now().plus(Duration.standardDays(1)));
    }

    public TrustAnchor createTypicalTa(KeyPair childKeyPair) {
        return createTrustAnchor(x -> {
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
        });
    }

    public TrustAnchor createTrustAnchor(Consumer<CertificateAuthority.CertificateAuthorityBuilder> configure) {
        return createTrustAnchor(configure, typicalValidityPeriod());
    }

    public TrustAnchor createTrustAnchor(Consumer<CertificateAuthority.CertificateAuthorityBuilder> configure, ValidityPeriod mftValidityPeriod) {
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

        X509ResourceCertificate certificate = createCertificateAuthority(root, root, mftValidityPeriod);

        TrustAnchor ta = new TrustAnchor();
        ta.setName(root.dn);
        ta.getLocations().add(root.certificateLocation);
        ta.setCertificate(certificate);
        ta.setSubjectPublicKeyInfo(X509CertificateUtil.getEncodedSubjectPublicKeyInfo(certificate.getCertificate()));
        return ta;
    }

    public X509ResourceCertificate createCertificateAuthority(CertificateAuthority ca, CertificateAuthority issuer) {
        return createCertificateAuthority(ca, issuer, typicalValidityPeriod());
    }

    public X509ResourceCertificate createCertificateAuthority(CertificateAuthority ca, CertificateAuthority issuer, ValidityPeriod mftValidityPeriod) {
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
                    .withValidityPeriod(typicalValidityPeriod())
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
            .withValidityPeriod(mftValidityPeriod)
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

    public X509ResourceCertificate createCaCertificate(CertificateAuthority ca, PublicKey publicKey, String issuerDN, String crlDistributionPoint, KeyPair signingKey) {
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

    public TrustAnchor createRipeNccTrustAnchor() {
        TrustAnchor ta = TrustAnchorValidationServiceTest.createRipeNccTrustAnchor();
        ta.setCertificate(RIPE_NCC_TA_CERTIFICATE);
        return ta;
    }

    public static BigInteger nextSerial() {
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
