package net.ripe.rpki.validator3.storage.encoding.custom;

import lombok.extern.slf4j.Slf4j;
import net.ripe.ipresource.IpResourceSet;
import net.ripe.rpki.commons.crypto.ValidityPeriod;
import net.ripe.rpki.commons.crypto.x509cert.X509ResourceCertificateBuilder;
import net.ripe.rpki.validator3.IntegrationTest;
import net.ripe.rpki.validator3.domain.ta.TrustAnchorsFactory;
import net.ripe.rpki.validator3.storage.data.RpkiObject;
import org.joda.time.DateTime;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.junit4.SpringRunner;

import javax.security.auth.x500.X500Principal;

import java.security.KeyPair;

import static net.ripe.rpki.validator3.domain.ta.TrustAnchorsFactory.KEY_PAIR_FACTORY;
import static org.junit.Assert.assertEquals;

@RunWith(SpringRunner.class)
@IntegrationTest
@Slf4j
public class RpkiObjectCoderTest {

    @Test
    public void testSaveRead() {
        KeyPair generate = KEY_PAIR_FACTORY.generate();
        RpkiObject rpkiObject = new RpkiObject(
                "rsync://localhost/orphan.cer",
                new X509ResourceCertificateBuilder()
                        .withResources(IpResourceSet.parse("10.0.0.0/8"))
                        .withIssuerDN(new X500Principal("CN=issuer"))
                        .withSubjectDN(new X500Principal("CN=orphan"))
                        .withSerial(TrustAnchorsFactory.nextSerial())
                        .withPublicKey(generate.getPublic())
                        .withSigningKeyPair(generate)
                        .withValidityPeriod(new ValidityPeriod(DateTime.now(), DateTime.now().plusYears(1)))
                        .build());

        RpkiObjectCoder coder = new RpkiObjectCoder();
        RpkiObject rpkiObject1 = coder.fromBytes(coder.toBytes(rpkiObject));

        assertEquals(rpkiObject, rpkiObject1);
    }

}