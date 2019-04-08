package net.ripe.rpki.validator3.storage.stores.impl;

import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.validator3.IntegrationTest;
import net.ripe.rpki.validator3.TestObjects;
import net.ripe.rpki.validator3.storage.TmpLmdb;
import net.ripe.rpki.validator3.storage.data.Ref;
import net.ripe.rpki.validator3.storage.data.TrustAnchor;
import net.ripe.rpki.validator3.storage.data.validation.CertificateTreeValidationRun;
import net.ripe.rpki.validator3.storage.data.validation.ValidationRun;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

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

    @Test
    public void testLatestSuccessful() throws Exception {

        final TrustAnchor trustAnchor = TestObjects.newTrustAnchor();
        wtx0(tx -> getTrustAnchorStore().add(tx, trustAnchor));

        final Ref<TrustAnchor> trustAnchorRef = rtx(tx -> getTrustAnchorStore().makeRef(tx, trustAnchor.key()));
        ValidationRun vr1 = wtx(tx -> {
            ValidationRun vr = new CertificateTreeValidationRun(trustAnchorRef);
            vr.setSucceeded();
            getValidationRunStore().add(tx, vr);
            return vr;
        });

        Thread.sleep(5);

        List<CertificateTreeValidationRun> rtx1 = rtx(tx -> getValidationRunStore().findAll(tx, CertificateTreeValidationRun.class));

        ValidationRun vr2 = wtx(tx -> {
            ValidationRun vr = new CertificateTreeValidationRun(trustAnchorRef);
            vr.setSucceeded();
            getValidationRunStore().add(tx, vr);
            return vr;
        });

        Thread.sleep(5);

        List<CertificateTreeValidationRun> rtx2 = rtx(tx -> getValidationRunStore().findAll(tx, CertificateTreeValidationRun.class));

        ValidationRun vr3 = wtx(tx -> {
            ValidationRun vr = new CertificateTreeValidationRun(trustAnchorRef);
            vr.setFailed();
            getValidationRunStore().add(tx, vr);
            return vr;
        });

        List<CertificateTreeValidationRun> rtx3 = rtx(tx -> getValidationRunStore().findAll(tx, CertificateTreeValidationRun.class));

        rtx0(tx -> {
            List<CertificateTreeValidationRun> latestSuccessful = getValidationRunStore().findLatestSuccessful(tx, CertificateTreeValidationRun.class);
            assertEquals(1, latestSuccessful.size());
            assertEquals(vr2, latestSuccessful.get(0));
        });


    }
}