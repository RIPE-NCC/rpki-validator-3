package net.ripe.rpki.validator3.adapter.jpa;

import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.validator3.TestObjects;
import net.ripe.rpki.validator3.domain.TrustAnchor;
import net.ripe.rpki.validator3.domain.TrustAnchors;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.ObjectRetrievalFailureException;
import org.springframework.test.context.junit4.SpringRunner;

import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

@RunWith(SpringRunner.class)
@SpringBootTest
@Transactional
@Slf4j
public class JPATrustAnchorRepositoryTest {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TrustAnchors subject;

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
        TrustAnchor trustAnchor = TestObjects.newTrustAnchor();
        entityManager.persist(trustAnchor);

        List<TrustAnchor> byName = subject.findByName("Trust Anchor");
        assertThat(byName).isNotEmpty();
        assertThat(byName.get(0)).isEqualTo(trustAnchor);
    }

}
