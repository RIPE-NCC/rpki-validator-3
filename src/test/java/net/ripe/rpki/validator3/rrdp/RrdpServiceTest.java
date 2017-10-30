package net.ripe.rpki.validator3.rrdp;

import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.generator.InRange;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import net.ripe.rpki.validator3.TestObjects;
import net.ripe.rpki.validator3.domain.RpkiObject;
import net.ripe.rpki.validator3.domain.RpkiObjects;
import net.ripe.rpki.validator3.domain.RpkiRepository;
import net.ripe.rpki.validator3.domain.RpkiRepositoryValidationRun;
import net.ripe.rpki.validator3.domain.TrustAnchor;
import net.ripe.rpki.validator3.util.Sha256;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import java.io.IOException;
import java.io.InputStream;
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
        final PublishInfo cert = new PublishInfo("rsync://host/path/cert.cer", Objects.aParseableCertificate());
        final PublishInfo crl = new PublishInfo("rsync://host/path/crl1.crl", Objects.aParseableCrl());
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
        final PublishInfo cert = new PublishInfo("rsync://host/path/cert.cer", Objects.aParseableCertificate());
        final PublishInfo crl = new PublishInfo("rsync://host/path/crl1.crl", Objects.aParseableCrl());
        rrdpClient.add(cert.uri, cert.content);
        rrdpClient.add(crl.uri, crl.content);

        final int serial = 1;
        final String sessionId = UUID.randomUUID().toString();

        final byte[] emptySnapshotXml = snapshotXml(serial, sessionId);

        final SnapshotInfo emptySnapshot = new SnapshotInfo("https://host/path/snapshot.xml", Sha256.hash(emptySnapshotXml));
        rrdpClient.add(emptySnapshot.uri, emptySnapshotXml);

        final byte[] notificationXml = notificationXml(serial, sessionId, emptySnapshot);
        final String notificationUri = "https://rrdp.ripe.net/notification.xml";
        rrdpClient.add(notificationUri, notificationXml);

        final TrustAnchor trustAnchor = TestObjects.newTrustAnchor();
        entityManager.persist(trustAnchor);

        final RpkiRepository rpkiRepository = new RpkiRepository(trustAnchor, notificationUri);
        entityManager.persist(rpkiRepository);

        final RpkiRepositoryValidationRun validationRun = new RpkiRepositoryValidationRun(rpkiRepository);
        subject.storeRepository(rpkiRepository, validationRun);

        final List<RpkiObject> objects = rpkiObjects.all();
        assertEquals(0, objects.size());

    }


    @Property
    public void parse_notification_snapshot_no_deltas(
            @InRange(minInt = 1, maxInt = 10) int serial,
            @InRange(minInt = 1, maxInt = 10) int publish,
            @InRange(minInt = 1, maxInt = 10) int withdraw) {

        final String certUrl = "rsync://host/path/bla.cer";
        rrdpClient.add(certUrl, Objects.aParseableCertificate());

        final String crlUrl = "rsync://host/path/bla.cer";
        rrdpClient.add(crlUrl, Objects.aParseableCrl());

        final String notificationUrl = "https://rrdp.ripe.net/notification.xml";
//        rrdpClient.add(notificationUrl, notificationXml());

    }


    @Property
    public void parse_notification_snapshot_delta(
            @InRange(minInt = 1, maxInt = 10) int serial,
            @InRange(minInt = 1, maxInt = 10) int publish,
            @InRange(minInt = 1, maxInt = 10) int withdraw) {



        final String notificationUrl = "https://rrdp.ripe.net/notification.xml";
//        rrdpClient.add(notificationUrl, notificationXml());

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

    private static class PublishInfo {
        public final String uri;
        public final byte[] content;

        private PublishInfo(String uri, byte[] content) {
            this.uri = uri;
            this.content = content;
        }
    }

    private byte[] notificationXml(int serial, String sessionId, SnapshotInfo snapshot, DeltaInfo... deltas) {
        final StringBuilder sb = new StringBuilder();
        sb.append("<notification xmlns=\"HTTP://www.ripe.net/rpki/rrdp\" version=\"1\" session_id=\"").append(sessionId).append("\" serial=\"").append(serial).append("\">");
        sb.append("    <snapshot uri=\"").append(snapshot.uri).append("\" hash=\"").append(Sha256.format(snapshot.hash)).append("\"/>");
        for (DeltaInfo di : deltas) {
            sb.append("  <delta uri=\"").append(di.uri).append("\" hash=\"").append(Sha256.format(di.hash)).append("\" serial=\"").append(di.serial).append("\"/>");
        }
        sb.append("</notification>");
        return sb.toString().getBytes(Charset.forName("UTF-8"));
    }

    private byte[] snapshotXml(int serial, String sessionId, PublishInfo... publishes) {
        final StringBuilder sb = new StringBuilder();
        sb.append("<snapshot xmlns=\"http://www.ripe.net/rpki/rrdp\" version=\"1\" session_id=\"").append(sessionId).append("\" serial=\"").append(serial).append("\">");
        for (PublishInfo publish : publishes) {
            sb.append("  <publish uri=\"").append(publish.uri).append("\">\n    ").append(Objects.encode(publish.content)).append("\n</publish>\n");
        }
        sb.append("</snapshot>");
        return sb.toString().getBytes(Charset.forName("UTF-8"));
    }

}