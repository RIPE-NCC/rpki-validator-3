package net.ripe.rpki.validator3.rrdp;

import net.ripe.rpki.validator3.TestObjects;
import net.ripe.rpki.validator3.domain.RpkiObject;
import net.ripe.rpki.validator3.domain.RpkiObjects;
import net.ripe.rpki.validator3.domain.RpkiRepository;
import net.ripe.rpki.validator3.domain.RpkiRepositoryValidationRun;
import net.ripe.rpki.validator3.domain.TrustAnchor;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(SpringRunner.class)
@SpringBootTest
@Transactional
public class RrdpServiceTest {

    @Autowired
    private RrdpService subject;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private RpkiObjects rpkiObjects;

    @Test
    public void should_parse_and_save_snapshot() throws Exception {
        final TrustAnchor trustAnchor = TestObjects.newTrustAnchor();
        entityManager.persist(trustAnchor);

        final RpkiRepository rpkiRepository = new RpkiRepository(trustAnchor, "https://rrdp.ripe.net/notification.xml");
        entityManager.persist(rpkiRepository);

        final RpkiRepositoryValidationRun validationRun = new RpkiRepositoryValidationRun(rpkiRepository);

        final Snapshot snapshot = new RrdpParser().snapshot(fileIS("rrdp/snapshot2.xml"));
        subject.storeSnapshot(rpkiRepository, snapshot, validationRun);

        final List<RpkiObject> objects = rpkiObjects.all();
        assertEquals(3, objects.size());

        final String uri1 = "rsync://rpki.ripe.net/repository/DEFAULT/61/fdce4c-2ea5-47eb-94bc-5b50ea88eeab/1/phQ5JfV8llJoaGylcrBcVa7oPfI.roa";
        assertTrue(objects.stream().anyMatch(o -> uri1.equals(o.getLocations().first())));

        final String uri2 = "rsync://rpki.ripe.net/repository/DEFAULT/a0/bf69c4-d64a-4340-9bf1-364854cbc0e8/1/Xt2pFufQkzxVnLyxgKKC8x5dVsw.mft";
        assertTrue(objects.stream().anyMatch(o -> uri2.equals(o.getLocations().first())));

        final String uri3 = "rsync://rpki.ripe.net/repository/DEFAULT/8f/db5787-c2c8-429b-8137-cbf6c1849c44/1/s70Ab2nV-TCWnoHVAM4QdNgMolQ.mft";
        assertTrue(objects.stream().anyMatch(o -> uri3.equals(o.getLocations().first())));
    }

    private static InputStream fileIS(final String path) throws IOException {
        return Thread.currentThread().getContextClassLoader().getResourceAsStream(path);
    }

}