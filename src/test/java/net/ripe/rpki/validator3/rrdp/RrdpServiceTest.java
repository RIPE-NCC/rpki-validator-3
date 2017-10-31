package net.ripe.rpki.validator3.rrdp;

import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.generator.InRange;
import fj.P;
import net.ripe.rpki.validator3.TestObjects;
import net.ripe.rpki.validator3.domain.RpkiObject;
import net.ripe.rpki.validator3.domain.RpkiObjects;
import net.ripe.rpki.validator3.domain.RpkiRepository;
import net.ripe.rpki.validator3.domain.RpkiRepositoryValidationRun;
import net.ripe.rpki.validator3.domain.TrustAnchor;
import net.ripe.rpki.validator3.util.Sha256;
import org.junit.Before;
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
import java.nio.charset.Charset;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(SpringRunner.class)
@SpringBootTest
@Transactional
public class RrdpServiceTest {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private RpkiObjects rpkiObjects;

    @Autowired
    private RpkiObjects rpkiObjectRepository;

    private RrdpClientStub rrdpClient = new RrdpClientStub();

    private RrdpService subject;

    @Before
    public void setUp() throws Exception {
        subject = new RrdpService(rrdpClient, rpkiObjectRepository);
    }

    @Test
    public void should_parse_and_save_snapshot() throws Exception {
        final TrustAnchor trustAnchor = TestObjects.newTrustAnchor();
        entityManager.persist(trustAnchor);

        final RpkiRepository rpkiRepository = new RpkiRepository(trustAnchor, "https://rrdp.ripe.net/notification.xml");
        entityManager.persist(rpkiRepository);

        final RpkiRepositoryValidationRun validationRun = new RpkiRepositoryValidationRun(rpkiRepository);

        final Snapshot snapshot = new RrdpParser().snapshot(fileIS("rrdp/snapshot2.xml"));
        subject.storeSnapshot(snapshot, validationRun);

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

    @Test
    public void should_parse_notification_and_snapshot() {
        final Publish cert = new Publish("rsync://host/path/cert.cer", Objects.aParseableCertificate());
        final Publish crl = new Publish("rsync://host/path/crl1.crl", Objects.aParseableCrl());
        rrdpClient.add(cert.uri, cert.content);
        rrdpClient.add(crl.uri, crl.content);

        final int serial = 1;
        final String sessionId = UUID.randomUUID().toString();
        final byte[] snapshotXml = snapshotXml(serial, sessionId, cert, crl);

        final SnapshotInfo snapshot = new SnapshotInfo("https://host/path/snapshot.xml", Sha256.hash(snapshotXml));
        rrdpClient.add(snapshot.uri, snapshotXml);

        final byte[] notificationXml = notificationXml(serial, sessionId, snapshot);
        final String notificationUri = "https://rrdp.ripe.net/notification.xml";
        rrdpClient.add(notificationUri, notificationXml);

        final TrustAnchor trustAnchor = TestObjects.newTrustAnchor();
        entityManager.persist(trustAnchor);

        final RpkiRepository rpkiRepository = new RpkiRepository(trustAnchor, notificationUri);
        entityManager.persist(rpkiRepository);

        final RpkiRepositoryValidationRun validationRun = new RpkiRepositoryValidationRun(rpkiRepository);
        subject.storeRepository(rpkiRepository, validationRun);

        final List<RpkiObject> objects = rpkiObjects.all();
        assertEquals(2, objects.size());

        assertTrue(objects.stream().anyMatch(o -> cert.uri.equals(o.getLocations().first())));
        assertTrue(objects.stream().anyMatch(o -> crl.uri.equals(o.getLocations().first())));
    }


    @Test
    public void should_parse_notification_use_delta() {
        final byte[] certificate = Objects.aParseableCertificate();
        final long serial = 2;
        final String sessionId = UUID.randomUUID().toString();
        final byte[] emptySnapshotXml = snapshotXml(serial, sessionId);

        final SnapshotInfo emptySnapshot = new SnapshotInfo("https://host/path/snapshot.xml", Sha256.hash(emptySnapshotXml));
        rrdpClient.add(emptySnapshot.uri, emptySnapshotXml);

        final DeltaPublish publishCert = new DeltaPublish("rsync://host/path/cert.cer", certificate);
        final byte[] deltaXml = deltaXml(serial, sessionId, publishCert);

        final DeltaInfo deltaInfo = new DeltaInfo("https://host/path/delta1.xml", Sha256.hash(deltaXml), serial);
        rrdpClient.add(deltaInfo.uri, deltaXml);

        final String notificationUri = "https://rrdp.ripe.net/notification.xml";
        rrdpClient.add(notificationUri, notificationXml(serial, sessionId, emptySnapshot, deltaInfo));

        final TrustAnchor trustAnchor = TestObjects.newTrustAnchor();
        entityManager.persist(trustAnchor);

        // make current serial lower to trigger delta download
        final RpkiRepository rpkiRepository = new RpkiRepository(trustAnchor, notificationUri);
        rpkiRepository.setRrdpSerial(BigInteger.valueOf(serial - 1));
        rpkiRepository.setRrdpSessionId(sessionId);
        entityManager.persist(rpkiRepository);

        // do the first run to get the snapshot
        subject.storeRepository(rpkiRepository, new RpkiRepositoryValidationRun(rpkiRepository));

        final List<RpkiObject> objects = rpkiObjects.all();
        assertEquals(1, objects.size());
    }

    private static class SnapshotInfo {
        public final String uri;
        public final byte[] hash;

        private SnapshotInfo(String uri, byte[] hash) {
            this.uri = uri;
            this.hash = hash;
        }
    }

    private static class DeltaInfo {
        public final String uri;
        public final byte[] hash;
        public final long serial;

        private DeltaInfo(String uri, byte[] hash, long serial) {
            this.uri = uri;
            this.hash = hash;
            this.serial = serial;
        }
    }

    private static class Publish {
        public final String uri;
        public final byte[] content;

        private Publish(String uri, byte[] content) {
            this.uri = uri;
            this.content = content;
        }
    }

    private static class Change {
        public final String uri;

        private Change(String uri) {
            this.uri = uri;
        }
    }

    private static class DeltaPublish extends Change {
        public final byte[] hash;
        public final byte[] content;

        private DeltaPublish(String uri, byte[] hash, byte[] content) {
            super(uri);
            this.hash = hash;
            this.content = content;
        }

        private DeltaPublish(String uri, byte[] content) {
            super(uri);
            hash = null;
            this.content = content;
        }
    }

    private static class DeltaWithdraw extends Change {
        public final byte[] hash;

        private DeltaWithdraw(String uri, byte[] hash) {
            super(uri);
            this.hash = hash;
        }
    }

    private byte[] notificationXml(long serial, String sessionId, SnapshotInfo snapshot, DeltaInfo... deltas) {
        final StringBuilder sb = new StringBuilder();
        sb.append("<notification xmlns=\"HTTP://www.ripe.net/rpki/rrdp\" version=\"1\" session_id=\"").append(sessionId).append("\" serial=\"").append(serial).append("\">");
        sb.append("    <snapshot uri=\"").append(snapshot.uri).append("\" hash=\"").append(Sha256.format(snapshot.hash)).append("\"/>");
        for (DeltaInfo di : deltas) {
            sb.append("  <delta uri=\"").append(di.uri).append("\" hash=\"").append(Sha256.format(di.hash)).append("\" serial=\"").append(di.serial).append("\"/>");
        }
        sb.append("</notification>");
        return sb.toString().getBytes(Charset.forName("UTF-8"));
    }

    private byte[] snapshotXml(long serial, String sessionId, Publish... publishes) {
        final StringBuilder sb = new StringBuilder();
        sb.append("<snapshot xmlns=\"http://www.ripe.net/rpki/rrdp\" version=\"1\" session_id=\"").append(sessionId).append("\" serial=\"").append(serial).append("\">");
        for (Publish publish : publishes) {
            sb.append("  <publish uri=\"").append(publish.uri).append("\">\n    ").append(Objects.encode(publish.content)).append("\n</publish>\n");
        }
        sb.append("</snapshot>");
        return sb.toString().getBytes(Charset.forName("UTF-8"));
    }

    private byte[] deltaXml(long serial, String sessionId, Change... updates) {
        final StringBuilder sb = new StringBuilder();
        sb.append("<delta xmlns=\"http://www.ripe.net/rpki/rrdp\" version=\"1\" session_id=\"").append(sessionId).append("\" serial=\"").append(serial).append("\">");
        for (Change change : updates) {
            if (change instanceof DeltaPublish) {
                DeltaPublish publish = (DeltaPublish) change;
                if (publish.hash != null) {
                    sb.append("  <publish uri=\"").append(publish.uri).
                            append("\" hash=\"").append(Sha256.format(publish.hash)).append("\">\n    ").
                            append(Objects.encode(publish.content)).
                            append("\n</publish>\n");
                } else {
                    sb.append("  <publish uri=\"").append(publish.uri).append("\">\n    ").
                            append(Objects.encode(publish.content)).
                            append("\n</publish>\n");
                }
            } else if (change instanceof DeltaWithdraw) {
                DeltaWithdraw withdraw = (DeltaWithdraw) change;
                sb.append("  <withdraw uri=\"").append(withdraw.uri).
                        append("\" hash=\"").append(Sha256.format(withdraw.hash)).append("\"/>");
            }
        }
        sb.append("</delta>");
        return sb.toString().getBytes(Charset.forName("UTF-8"));
    }

}