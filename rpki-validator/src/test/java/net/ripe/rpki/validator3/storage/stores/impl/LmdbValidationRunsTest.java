package net.ripe.rpki.validator3.storage.stores.impl;

import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.validator3.IntegrationTest;
import net.ripe.rpki.validator3.TestObjects;
import net.ripe.rpki.validator3.storage.data.TrustAnchor;
import net.ripe.rpki.validator3.storage.data.validation.CertificateTreeValidationRun;
import net.ripe.rpki.validator3.storage.data.validation.ValidationRun;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.junit4.SpringRunner;

import static org.junit.Assert.assertEquals;

@RunWith(SpringRunner.class)
@IntegrationTest
@Slf4j
public class LmdbValidationRunsTest extends GenericStorageTest {

    @Test
    public void testAddUpdate() {
        final TrustAnchor trustAnchor = TestObjects.newTrustAnchor();
        wtx0(tx -> getTrustAnchorStore().add(tx, trustAnchor));

        ValidationRun validationRun = wtx(tx -> {
            ValidationRun vr = new CertificateTreeValidationRun(getTrustAnchorStore().makeRef(tx, trustAnchor.key()));
            getValidationRunStore().add(tx, vr);
            return vr;
        });

        rtx0(tx -> {
            CertificateTreeValidationRun actual = getValidationRunStore().get(tx,
                    CertificateTreeValidationRun.class, validationRun.key().asLong());
            assertEquals(validationRun, actual);
        });

        validationRun.setSucceeded();
        wtx0(tx -> getValidationRunStore().update(tx, validationRun));

        rtx0(tx -> {
            CertificateTreeValidationRun actual = getValidationRunStore().get(tx,
                    CertificateTreeValidationRun.class, validationRun.key().asLong());
            assertEquals(validationRun, actual);
        });
    }
}