package net.ripe.rpki.validator3.domain.validation;

import net.ripe.rpki.commons.crypto.x509cert.X509ResourceCertificate;
import net.ripe.rpki.validator3.domain.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import javax.transaction.Transactional;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(SpringRunner.class)
@SpringBootTest
@Transactional
public class TrustAnchorValidationServiceTest {

    private static final String DUMMY_RSYNC_URI = "rsync://localhost/non-existent/ta/ripe-ncc-ta.cer";

    @Autowired
    private TrustAnchors trustAnchors;

    @Autowired
    private TrustAnchorValidationService subject;

    @Autowired
    private ValidationRuns validationRuns;

    @Autowired
    private RpkiRepositories rpkiRepositories;

    @Test
    public void test_success() {
        TrustAnchor ta = createRipeNccTrustAnchor();

        ta.setLocations(Arrays.asList("src/test/resources/ripe-ncc-ta.cer"));
        subject.validate(ta.getId());
        ta.setLocations(Arrays.asList(DUMMY_RSYNC_URI));

        X509ResourceCertificate certificate = ta.getCertificate();
        assertThat(certificate).isNotNull();

        Optional<TrustAnchorValidationRun> validationRun = validationRuns.findLatestCompletedForTrustAnchor(ta);
        assertThat(validationRun).isPresent();
        assertThat(validationRun.get().getStatus()).isEqualTo(ValidationRun.Status.SUCCEEDED);

        assertThat(validationRun.get().getValidationChecks()).isEmpty();
    }

    @Test
    public void test_rsync_failure() {
        TrustAnchor ta = createRipeNccTrustAnchor();
        ta.setLocations(Arrays.asList(DUMMY_RSYNC_URI));

        subject.validate(ta.getId());

        assertThat(ta.getCertificate()).isNull();

        Optional<TrustAnchorValidationRun> validationRun = validationRuns.findLatestCompletedForTrustAnchor(ta);
        assertThat(validationRun).isPresent();
        assertThat(validationRun.get().getStatus()).isEqualTo(ValidationRun.Status.FAILED);

        List<ValidationCheck> validationChecks = validationRun.get().getValidationChecks();
        assertThat(validationChecks).hasSize(1);
        assertThat(validationChecks.get(0).getKey()).isEqualTo("rsync.error");
    }

    @Test
    public void test_empty_file() {
        TrustAnchor ta = createRipeNccTrustAnchor();

        ta.setLocations(Arrays.asList("src/test/resources/empty-file.cer"));
        subject.validate(ta.getId());
        ta.setLocations(Arrays.asList(DUMMY_RSYNC_URI));

        assertThat(ta.getCertificate()).isNull();

        Optional<TrustAnchorValidationRun> validationRun = validationRuns.findLatestCompletedForTrustAnchor(ta);
        assertThat(validationRun).isPresent();
        assertThat(validationRun.get().getStatus()).isEqualTo(ValidationRun.Status.FAILED);

        List<ValidationCheck> validationChecks = validationRun.get().getValidationChecks();
        assertThat(validationChecks).hasSize(1);
        assertThat(validationChecks.get(0).getKey()).isEqualTo("repository.object.empty");
    }

    @Test
    public void test_bad_subject_public_key() {
        TrustAnchor ta = createRipeNccTrustAnchor();
        ta.setSubjectPublicKeyInfo(ta.getSubjectPublicKeyInfo().toUpperCase());

        ta.setLocations(Arrays.asList("src/test/resources/ripe-ncc-ta.cer"));
        subject.validate(ta.getId());
        ta.setLocations(Arrays.asList(DUMMY_RSYNC_URI));

        assertThat(ta.getCertificate()).isNull();

        Optional<TrustAnchorValidationRun> validationRun = validationRuns.findLatestCompletedForTrustAnchor(ta);
        assertThat(validationRun).isPresent();
        assertThat(validationRun.get().getStatus()).isEqualTo(ValidationRun.Status.FAILED);

        List<ValidationCheck> validationChecks = validationRun.get().getValidationChecks();
        assertThat(validationChecks).hasSize(1);
        assertThat(validationChecks.get(0).getKey()).isEqualTo("trust.anchor.subject.key.matches.locator");
    }

    private TrustAnchor createRipeNccTrustAnchor() {
        TrustAnchor ta = new TrustAnchor();
        ta.setName("RIPE NCC");
        ta.setLocations(Arrays.asList("rsync://rpki.ripe.net/ta/ripe-ncc-ta.cer"));
        ta.setSubjectPublicKeyInfo("MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA0URYSGqUz2myBsOzeW1jQ6NsxNvlLMyhWknvnl8NiBCs/T/S2XuNKQNZ+wBZxIgPPV2pFBFeQAvoH/WK83HwA26V2siwm/MY2nKZ+Olw+wlpzlZ1p3Ipj2eNcKrmit8BwBC8xImzuCGaV0jkRB0GZ0hoH6Ml03umLprRsn6v0xOP0+l6Qc1ZHMFVFb385IQ7FQQTcVIxrdeMsoyJq9eMkE6DoclHhF/NlSllXubASQ9KUWqJ0+Ot3QCXr4LXECMfkpkVR2TZT+v5v658bHVs6ZxRD1b6Uk1uQKAyHUbn/tXvP8lrjAibGzVsXDT2L0x4Edx+QdixPgOji3gBMyL2VwIDAQAB");
        trustAnchors.add(ta);
        return ta;
    }
}