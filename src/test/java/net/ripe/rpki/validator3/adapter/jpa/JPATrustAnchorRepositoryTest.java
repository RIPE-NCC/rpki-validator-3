package net.ripe.rpki.validator3.adapter.jpa;

import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.validator3.domain.TrustAnchor;
import net.ripe.rpki.validator3.domain.TrustAnchorRepository;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.ObjectRetrievalFailureException;
import org.springframework.test.context.junit4.SpringRunner;

import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

@RunWith(SpringRunner.class)
@SpringBootTest
@Transactional
@Slf4j
public class JPATrustAnchorRepositoryTest {

    private static final String RIPE_NCC_TRUST_ANCHOR_SUBJECT_PUBLIC_KEY_INFO = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA0URYSGqUz2myBsOzeW1jQ6NsxNvlLMyhWknvnl8NiBCs/T/S2XuNKQNZ+wBZxIgPPV2pFBFeQAvoH/WK83HwA26V2siwm/MY2nKZ+Olw+wlpzlZ1p3Ipj2eNcKrmit8BwBC8xImzuCGaV0jkRB0GZ0hoH6Ml03umLprRsn6v0xOP0+l6Qc1ZHMFVFb385IQ7FQQTcVIxrdeMsoyJq9eMkE6DoclHhF/NlSllXubASQ9KUWqJ0+Ot3QCXr4LXECMfkpkVR2TZT+v5v658bHVs6ZxRD1b6Uk1uQKAyHUbn/tXvP8lrjAibGzVsXDT2L0x4Edx+QdixPgOji3gBMyL2VwIDAQAB";

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TrustAnchorRepository subject;

    @After
    public void tearDown() {
        entityManager.flush();
    }

    @Test
    public void should_use_spring_data_access_Exceptions() {
        log.info("subject {}", subject.getClass());
        assertThatExceptionOfType(ObjectRetrievalFailureException.class).isThrownBy(() -> subject.get(-4));
    }

    @Test
    public void should_find_trust_anchors_by_case_insensitive_name() {
        TrustAnchor trustAnchor = newTrustAnchor();
        entityManager.persist(trustAnchor);

        List<TrustAnchor> byName = subject.findByName("Trust Anchor");
        assertThat(byName).isNotEmpty();
        assertThat(byName.get(0)).isEqualTo(trustAnchor);
    }

    private TrustAnchor newTrustAnchor() {
        TrustAnchor trustAnchor = new TrustAnchor();
        trustAnchor.setName("trust anchor");
        trustAnchor.setLocations(Arrays.asList("rsync://rpki.test/trust-anchor.cer"));
        trustAnchor.setSubjectPublicKeyInfo(RIPE_NCC_TRUST_ANCHOR_SUBJECT_PUBLIC_KEY_INFO);
        return trustAnchor;
    }
}
