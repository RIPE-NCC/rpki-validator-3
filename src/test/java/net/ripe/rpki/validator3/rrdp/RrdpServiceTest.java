package net.ripe.rpki.validator3.rrdp;

import net.ripe.rpki.validator3.TestObjects;
import net.ripe.rpki.validator3.domain.RpkiObject;
import net.ripe.rpki.validator3.domain.RpkiObjects;
import net.ripe.rpki.validator3.domain.RpkiRepository;
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
import java.math.BigInteger;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

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
    public void should_parse_snapshot() throws Exception {
        TrustAnchor trustAnchor = TestObjects.newTrustAnchor();
        entityManager.persist(trustAnchor);

        final RpkiRepository rpkiRepository = new RpkiRepository(trustAnchor, "https://rrdp.ripe.net/notification.xml");
        entityManager.persist(rpkiRepository);

        final Snapshot snapshot = new RrdpParser().snapshot(fileIS("rrdp/snapshot2.xml"));
        subject.storeSnapshot(rpkiRepository, snapshot);

        final List<RpkiObject> objects = rpkiObjects.all();
        assertEquals(3, objects.size());
    }

    private static InputStream fileIS(String path) throws IOException {
        return Thread.currentThread().getContextClassLoader().getResourceAsStream(path);
    }

}