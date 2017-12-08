package net.ripe.rpki.validator3.domain;

import net.ripe.ipresource.Asn;
import net.ripe.ipresource.IpRange;
import net.ripe.ipresource.IpResourceSet;
import net.ripe.rpki.commons.crypto.ValidityPeriod;
import net.ripe.rpki.commons.crypto.x509cert.X509ResourceCertificateBuilder;
import net.ripe.rpki.validator3.IntegrationTest;
import org.joda.time.DateTime;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit4.SpringRunner;

import javax.persistence.EntityManager;
import javax.security.auth.x500.X500Principal;
import javax.transaction.Transactional;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;

import static net.ripe.rpki.validator3.domain.TrustAnchorsFactory.KEY_PAIR_FACTORY;
import static org.assertj.core.api.Assertions.assertThat;

@RunWith(SpringRunner.class)
@IntegrationTest
@Transactional
public class RpkiObjectCleanupServiceTest {

    @Autowired
    private TrustAnchorsFactory factory;

    @Autowired
    private RpkiObjectCleanupService subject;

    @Autowired
    private RpkiObjects rpkiObjects;

    @Autowired
    private EntityManager entityManager;

    @Test
    public void should_delete_objects_not_reachable_from_manifest() {
        TrustAnchor trustAnchor = factory.createTrustAnchor(ta -> {
            ta.roaPrefixes(Arrays.asList(RoaPrefix.of(IpRange.parse("127.0.0.0/8"), null, Asn.parse("123"))));
        });

        RpkiObject orphan = new RpkiObject(
            "rsync://localhost/orphan.cer",
            new X509ResourceCertificateBuilder()
                .withResources(IpResourceSet.parse("10.0.0.0/8"))
                .withIssuerDN(trustAnchor.getCertificate().getSubject())
                .withSubjectDN(new X500Principal("CN=orphan"))
                .withSerial(factory.nextSerial())
                .withPublicKey(KEY_PAIR_FACTORY.generate().getPublic())
                .withSigningKeyPair(KEY_PAIR_FACTORY.generate())
                .withValidityPeriod(new ValidityPeriod(DateTime.now(), DateTime.now().plusYears(1)))
                .build()
        );
        orphan.markReachable(Instant.now().minus(Duration.ofDays(10)));
        rpkiObjects.add(orphan);
        entityManager.flush();

        long deletedCount = subject.cleanupRpkiObjects();

        assertThat(deletedCount).isEqualTo(1);
    }
}