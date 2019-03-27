package net.ripe.rpki.validator3.storage.stores.impl;

import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.validator3.IntegrationTest;
import net.ripe.rpki.validator3.TestObjects;
import net.ripe.rpki.validator3.api.SearchTerm;
import net.ripe.rpki.validator3.storage.data.RpkiRepository;
import net.ripe.rpki.validator3.storage.data.TrustAnchor;
import net.ripe.rpki.validator3.storage.lmdb.Tx;
import net.ripe.rpki.validator3.storage.stores.Id;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Map;

import static org.junit.Assert.assertEquals;

@RunWith(SpringRunner.class)
@IntegrationTest
@Slf4j
public class LmdbRpkiRepostioriesTest extends GenericStorageTest {

    private TrustAnchor trustAnchor;
    private RpkiRepository rsyncRepo;

    @Before
    public void setup() {
        trustAnchor = TestObjects.newTrustAnchor();
        wtx0(tx -> getTrustAnchorStore().add(tx, trustAnchor));

        rsyncRepo = Tx.with(getLmdb().writeTx(), tx -> getRpkiRepositoryStore().register(tx,
                trustAnchor, "rsync://some.rsync.repo", RpkiRepository.Type.RSYNC));
    }

    @Test
    public void after_setup_there_is_one_rpki_repository() {
        long countAll = rtx(tx -> getRpkiRepositoryStore().countAll(tx, RpkiRepository.Status.PENDING,
                trustAnchor.getId(), true, new SearchTerm("some")));
        assertEquals(1, countAll);
    }

    @Test
    public void after_setup_there_is_one_pending() {
        Map<RpkiRepository.Status, Long> statuses = rtx(tx -> getRpkiRepositoryStore().countByStatus(tx, trustAnchor.getId(), true));
        long countPending = statuses.get(RpkiRepository.Status.PENDING);
        assertEquals(1, countPending);
    }

    @Test
    public void after_remove_count_should_be_zero() {
        long countAll = wtx(tx -> {
            getRpkiRepositoryStore().removeAllForTrustAnchor(tx, trustAnchor);
            return getRpkiRepositoryStore().countAll(tx, RpkiRepository.Status.PENDING, trustAnchor.getId(), true, new SearchTerm("some"));
        });
        assertEquals(0, countAll);
    }

    @Test
    public void after_remove_repository_count_should_be_zero() {
        long countAll = wtx(tx -> {
            getRpkiRepositoryStore().remove(tx, rsyncRepo.getId());
            return getRpkiRepositoryStore().countAll(tx, RpkiRepository.Status.PENDING, trustAnchor.getId(), true, new SearchTerm("some"));
        });
        assertEquals(0, countAll);
    }

}