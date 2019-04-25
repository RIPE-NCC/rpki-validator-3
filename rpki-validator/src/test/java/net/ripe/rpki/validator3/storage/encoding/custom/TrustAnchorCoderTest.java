package net.ripe.rpki.validator3.storage.encoding.custom;

import lombok.extern.slf4j.Slf4j;
import net.ripe.ipresource.IpResourceSet;
import net.ripe.rpki.commons.crypto.ValidityPeriod;
import net.ripe.rpki.validator3.IntegrationTest;
import net.ripe.rpki.validator3.domain.ta.TrustAnchorsFactory;
import net.ripe.rpki.validator3.storage.data.TrustAnchor;
import net.ripe.rpki.validator3.storage.stores.impl.GenericStorageTest;
import org.joda.time.Duration;
import org.joda.time.Instant;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit4.SpringRunner;

import java.security.KeyPair;
import java.util.Collections;

import static net.ripe.rpki.validator3.domain.ta.TrustAnchorsFactory.KEY_PAIR_FACTORY;
import static net.ripe.rpki.validator3.domain.ta.TrustAnchorsFactory.TA_RRDP_NOTIFY_URI;
import static org.junit.Assert.assertEquals;

@RunWith(SpringRunner.class)
@IntegrationTest
@Slf4j
public class TrustAnchorCoderTest extends GenericStorageTest {

    @Autowired
    private TrustAnchorsFactory trustAnchorsFactory;

    @Test
    public void testSaveRead() {
        TrustAnchor trustAnchor = makeTa();

        TrustAnchorCoder coder = new TrustAnchorCoder();
        TrustAnchor trustAnchor1 = coder.fromBytes(coder.toBytes(trustAnchor));

        assertEquals(trustAnchor, trustAnchor1);
    }

    public TrustAnchor makeTa() {
        final KeyPair childKeyPair = KEY_PAIR_FACTORY.generate();

        final ValidityPeriod mftValidityPeriod = new ValidityPeriod(
                Instant.now().minus(Duration.standardDays(2)),
                Instant.now().minus(Duration.standardDays(1))
        );

        return wtx(tx -> {
            TrustAnchor ta1 = trustAnchorsFactory.createTrustAnchor(tx, x -> {
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
                x.children(Collections.singletonList(child));
            }, mftValidityPeriod);
            ta1.setInitialCertificateTreeValidationRunCompleted(true);
            ta1.setUpdatedAt(java.time.Instant.now());
            ta1.setCreatedAt(java.time.Instant.now().minus(java.time.Duration.ofDays(1)));
            getTrustAnchorStore().add(tx, ta1);
            return ta1;
        });
    }
}