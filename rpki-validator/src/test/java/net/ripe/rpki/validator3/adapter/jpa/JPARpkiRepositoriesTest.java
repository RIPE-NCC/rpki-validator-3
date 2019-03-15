package net.ripe.rpki.validator3.adapter.jpa;

import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.validator3.IntegrationTest;
import net.ripe.rpki.validator3.TestObjects;
import net.ripe.rpki.validator3.api.SearchTerm;
import net.ripe.rpki.validator3.domain.RpkiRepositories;
import net.ripe.rpki.validator3.domain.RpkiRepository;
import net.ripe.rpki.validator3.domain.TrustAnchor;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit4.SpringRunner;

import javax.persistence.EntityManager;
import javax.transaction.Transactional;

import java.util.Map;

import static org.junit.Assert.assertEquals;

@RunWith(SpringRunner.class)
@IntegrationTest
@Transactional
@Slf4j
public class JPARpkiRepositoriesTest {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private RpkiRepositories repositories;

    final TrustAnchor trustAnchor = TestObjects.newTrustAnchor();
    final RpkiRepository rsyncRepo = new RpkiRepository(trustAnchor, "rsync://some.rsync.repo", RpkiRepository.Type.RSYNC);

    @Before
    public void setup(){

        entityManager.persist(trustAnchor);
        entityManager.persist(rsyncRepo);
    }

    @Test
    public void after_setup_there_is_one_rpki_repository() {
        long countAll = repositories.countAll(RpkiRepository.Status.PENDING, trustAnchor.getId(), true, new SearchTerm("some"));
        assertEquals(1, countAll);
    }

    @Test
    public void after_setup_there_is_one_pending() {
        Map<RpkiRepository.Status, Long> statuses = repositories.countByStatus(trustAnchor.getId(), true);
        long countPending = statuses.get(RpkiRepository.Status.PENDING);
        assertEquals(1, countPending);
    }

    @Test
    public void after_remove_count_should_be_zero() {
        repositories.removeAllForTrustAnchor(trustAnchor);
        long countAll = repositories.countAll(RpkiRepository.Status.PENDING, trustAnchor.getId(), true, new SearchTerm("some"));
        assertEquals(0, countAll);
    }

    @Test
    public void after_remove_repository_count_should_be_zero() {
        repositories.remove(rsyncRepo.getId());
        long countAll = repositories.countAll(RpkiRepository.Status.PENDING, trustAnchor.getId(), true, new SearchTerm("some"));
        assertEquals(0, countAll);
    }
}