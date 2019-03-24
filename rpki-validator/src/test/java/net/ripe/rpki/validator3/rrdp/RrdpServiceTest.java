/**
 * The BSD License
 *
 * Copyright (c) 2010-2018 RIPE NCC
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *   - Redistributions of source code must retain the above copyright notice,
 *     this list of conditions and the following disclaimer.
 *   - Redistributions in binary form must reproduce the above copyright notice,
 *     this list of conditions and the following disclaimer in the documentation
 *     and/or other materials provided with the distribution.
 *   - Neither the name of the RIPE NCC nor the names of its contributors may be
 *     used to endorse or promote products derived from this software without
 *     specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package net.ripe.rpki.validator3.rrdp;

import com.google.common.collect.Sets;
import net.ripe.rpki.validator3.IntegrationTest;
import net.ripe.rpki.validator3.TestObjects;
import net.ripe.rpki.validator3.domain.ErrorCodes;
import net.ripe.rpki.validator3.domain.RpkiObject;
import net.ripe.rpki.validator3.domain.RpkiObjects;
import net.ripe.rpki.validator3.domain.RpkiRepository;
import net.ripe.rpki.validator3.domain.RrdpRepositoryValidationRun;
import net.ripe.rpki.validator3.domain.TrustAnchor;
import net.ripe.rpki.validator3.domain.ValidationCheck;
import net.ripe.rpki.validator3.storage.Lmdb;
import net.ripe.rpki.validator3.storage.stores.RpkiObjectsStore;
import net.ripe.rpki.validator3.util.Hex;
import net.ripe.rpki.validator3.util.Sha256;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit4.SpringRunner;

import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import java.math.BigInteger;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(SpringRunner.class)
@IntegrationTest
@Transactional
public class RrdpServiceTest {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private RpkiObjects rpkiObjects;

    @Autowired
    private RpkiObjects rpkiObjectRepository;

    @Autowired
    private RpkiObjectsStore rpkiObjectsStore;

    @Autowired
    private Lmdb lmdb;

    private RrdpClientStub rrdpClient = new RrdpClientStub();

    private RrdpService subject;

    @Before
    public void setUp() throws Exception {
        subject = new RrdpService(rrdpClient, rpkiObjectRepository, rpkiObjectsStore, lmdb);
    }

    @Test
    public void should_parse_and_save_snapshot() throws Exception {
        final TrustAnchor trustAnchor = TestObjects.newTrustAnchor();
        entityManager.persist(trustAnchor);

        final RpkiRepository rpkiRepository = new RpkiRepository(trustAnchor, "https://rrdp.ripe.net/notification.xml", RpkiRepository.Type.RRDP);
        entityManager.persist(rpkiRepository);

        final RrdpRepositoryValidationRun validationRun = new RrdpRepositoryValidationRun(rpkiRepository);

        final Snapshot snapshot = new RrdpParser().snapshot(Objects.fileIS("rrdp/snapshot2.xml"));
        subject.storeSnapshot(snapshot, validationRun);

        final List<RpkiObject> objects = rpkiObjects.all().collect(Collectors.toList());
        assertEquals(3, objects.size());

        final String uri1 = "rsync://rpki.ripe.net/repository/DEFAULT/61/fdce4c-2ea5-47eb-94bc-5b50ea88eeab/1/phQ5JfV8llJoaGylcrBcVa7oPfI.roa";
        assertTrue(objects.stream().anyMatch(o -> uri1.equals(o.getLocations().first())));

        final String uri2 = "rsync://rpki.ripe.net/repository/DEFAULT/a0/bf69c4-d64a-4340-9bf1-364854cbc0e8/1/Xt2pFufQkzxVnLyxgKKC8x5dVsw.mft";
        assertTrue(objects.stream().anyMatch(o -> uri2.equals(o.getLocations().first())));

        final String uri3 = "rsync://rpki.ripe.net/repository/DEFAULT/8f/db5787-c2c8-429b-8137-cbf6c1849c44/1/s70Ab2nV-TCWnoHVAM4QdNgMolQ.mft";
        assertTrue(objects.stream().anyMatch(o -> uri3.equals(o.getLocations().first())));
    }

    @Test
    public void should_parse_notification_and_snapshot() {
        final Objects.Publish cert = new Objects.Publish("rsync://host/path/cert.cer", Objects.aParseableCertificate());
        final Objects.Publish crl = new Objects.Publish("rsync://host/path/crl1.crl", Objects.aParseableCrl());
        rrdpClient.add(cert.uri, cert.content);
        rrdpClient.add(crl.uri, crl.content);

        final int serial = 1;
        final String sessionId = UUID.randomUUID().toString();
        final byte[] snapshotXml = Objects.snapshotXml(serial, sessionId, cert, crl);

        final Objects.SnapshotInfo snapshot = new Objects.SnapshotInfo("https://host/path/snapshot.xml", Sha256.hash(snapshotXml));
        rrdpClient.add(snapshot.uri, snapshotXml);

        final byte[] notificationXml = Objects.notificationXml(serial, sessionId, snapshot);
        final String notificationUri = "https://rrdp.ripe.net/notification.xml";
        rrdpClient.add(notificationUri, notificationXml);

        final TrustAnchor trustAnchor = TestObjects.newTrustAnchor();
        entityManager.persist(trustAnchor);

        final RpkiRepository rpkiRepository = new RpkiRepository(trustAnchor, notificationUri, RpkiRepository.Type.RRDP);
        entityManager.persist(rpkiRepository);

        final RrdpRepositoryValidationRun validationRun = new RrdpRepositoryValidationRun(rpkiRepository);
        subject.storeRepository(rpkiRepository, validationRun);

        final List<RpkiObject> objects = rpkiObjects.all().collect(Collectors.toList());
        assertEquals(2, objects.size());

        assertTrue(objects.stream().anyMatch(o -> cert.uri.equals(o.getLocations().first())));
        assertTrue(objects.stream().anyMatch(o -> crl.uri.equals(o.getLocations().first())));
    }

    @Test
    public void should_parse_notification_verify_snapshot_hash() {
        final Objects.Publish cert = new Objects.Publish("rsync://host/path/cert.cer", Objects.aParseableCertificate());
        final Objects.Publish crl = new Objects.Publish("rsync://host/path/crl1.crl", Objects.aParseableCrl());
        rrdpClient.add(cert.uri, cert.content);
        rrdpClient.add(crl.uri, crl.content);

        final int serial = 1;
        final String sessionId = UUID.randomUUID().toString();
        final byte[] snapshotXml = Objects.snapshotXml(serial, sessionId, cert, crl);

        final String snapshotUri = "https://host/path/snapshot.xml";
        final Objects.SnapshotInfo snapshot = new Objects.SnapshotInfo(snapshotUri, Hex.parse("FFFFFF"));
        rrdpClient.add(snapshot.uri, snapshotXml);

        final byte[] notificationXml = Objects.notificationXml(serial, sessionId, snapshot);
        final String notificationUri = "https://rrdp.ripe.net/notification.xml";
        rrdpClient.add(notificationUri, notificationXml);

        final TrustAnchor trustAnchor = TestObjects.newTrustAnchor();
        entityManager.persist(trustAnchor);

        final RpkiRepository rpkiRepository = new RpkiRepository(trustAnchor, notificationUri, RpkiRepository.Type.RRDP);
        entityManager.persist(rpkiRepository);

        final RrdpRepositoryValidationRun validationRun = new RrdpRepositoryValidationRun(rpkiRepository);
        subject.storeRepository(rpkiRepository, validationRun);

        final List<RpkiObject> objects = rpkiObjects.all().collect(Collectors.toList());
        assertEquals(0, objects.size());

        assertEquals(1, validationRun.getValidationChecks().size());
        final ValidationCheck validationCheck = validationRun.getValidationChecks().get(0);
        assertEquals(ErrorCodes.RRDP_FETCH, validationCheck.getKey());
        assertEquals(ValidationCheck.Status.ERROR, validationCheck.getStatus());
        assertEquals("Hash of the snapshot file " + snapshotUri + " is " + Hex.format(Sha256.hash(snapshotXml)) +
                ", but notification file says FFFFFF", validationCheck.getParameters().get(0));
        assertEquals(rpkiRepository.getRrdpNotifyUri(), validationCheck.getLocation());
    }

    @Test
    public void should_parse_notification_use_delta() {
        final byte[] certificate = Objects.aParseableCertificate();
        final long serial = 2;
        final String sessionId = UUID.randomUUID().toString();
        final byte[] emptySnapshotXml = Objects.snapshotXml(serial, sessionId);

        final Objects.SnapshotInfo emptySnapshot = new Objects.SnapshotInfo("https://host/path/snapshot.xml", Sha256.hash(emptySnapshotXml));
        rrdpClient.add(emptySnapshot.uri, emptySnapshotXml);

        final Objects.DeltaPublish publishCert = new Objects.DeltaPublish("rsync://host/path/cert.cer", certificate);
        final byte[] deltaXml = Objects.deltaXml(serial, sessionId, publishCert);

        final Objects.DeltaInfo deltaInfo = new Objects.DeltaInfo("https://host/path/delta1.xml", Sha256.hash(deltaXml), serial);
        rrdpClient.add(deltaInfo.uri, deltaXml);

        final String notificationUri = "https://rrdp.ripe.net/notification.xml";
        rrdpClient.add(notificationUri, Objects.notificationXml(serial, sessionId, emptySnapshot, deltaInfo));

        final TrustAnchor trustAnchor = TestObjects.newTrustAnchor();
        entityManager.persist(trustAnchor);

        // make current serial lower to trigger delta download
        final RpkiRepository rpkiRepository = new RpkiRepository(trustAnchor, notificationUri, RpkiRepository.Type.RRDP);
        rpkiRepository.setRrdpSerial(BigInteger.valueOf(serial - 1));
        rpkiRepository.setRrdpSessionId(sessionId);
        entityManager.persist(rpkiRepository);

        // do the first run to get the snapshot
        RrdpRepositoryValidationRun validationRun = new RrdpRepositoryValidationRun(rpkiRepository);
        subject.storeRepository(rpkiRepository, validationRun);
        assertEquals(0, validationRun.getValidationChecks().size());

        final List<RpkiObject> objects = rpkiObjects.all().collect(Collectors.toList());
        assertEquals(1, objects.size());
    }

    @Test
    public void should_parse_notification_use_decline_delta_with_different_session_id_and_fallback_to_snapshot() {
        final byte[] certificate = Objects.aParseableCertificate();
        final long serial = 2;
        final String sessionId = UUID.randomUUID().toString();
        final String wrongSessionId = UUID.randomUUID().toString();

        final Objects.Publish crl = new Objects.Publish("rsync://host/path/crl1.crl", Objects.aParseableCrl());
        rrdpClient.add(crl.uri, crl.content);

        final byte[] snapshotXml = Objects.snapshotXml(serial, sessionId, crl);
        final Objects.SnapshotInfo snapshot = new Objects.SnapshotInfo("https://host/path/snapshot.xml", Sha256.hash(snapshotXml));
        rrdpClient.add(snapshot.uri, snapshotXml);

        final Objects.DeltaPublish publishCert = new Objects.DeltaPublish("rsync://host/path/cert.cer", certificate);
        final byte[] deltaXml = Objects.deltaXml(serial, wrongSessionId, publishCert);

        final Objects.DeltaInfo deltaInfo = new Objects.DeltaInfo("https://host/path/delta1.xml", Sha256.hash(deltaXml), serial);
        rrdpClient.add(deltaInfo.uri, deltaXml);

        final String notificationUri = "https://rrdp.ripe.net/notification.xml";
        rrdpClient.add(notificationUri, Objects.notificationXml(serial, sessionId, snapshot, deltaInfo));

        final TrustAnchor trustAnchor = TestObjects.newTrustAnchor();
        entityManager.persist(trustAnchor);

        // make current serial lower to trigger delta download
        final RpkiRepository rpkiRepository = new RpkiRepository(trustAnchor, notificationUri, RpkiRepository.Type.RRDP);
        rpkiRepository.setRrdpSerial(BigInteger.valueOf(serial - 1));
        rpkiRepository.setRrdpSessionId(sessionId);
        entityManager.persist(rpkiRepository);

        // do the first run to get the snapshot
        final RrdpRepositoryValidationRun validationRun = new RrdpRepositoryValidationRun(rpkiRepository);
        subject.storeRepository(rpkiRepository, validationRun);

        assertEquals(1, validationRun.getValidationChecks().size());
        final ValidationCheck validationCheck = validationRun.getValidationChecks().get(0);
        assertEquals(ErrorCodes.RRDP_WRONG_DELTA_SESSION, validationCheck.getKey());
        assertEquals(ValidationCheck.Status.WARNING, validationCheck.getStatus());
        assertEquals(rpkiRepository.getRrdpNotifyUri(), validationCheck.getLocation());
        assertTrue(validationCheck.getParameters().get(0).contains("Session id of the delta"));
        assertTrue(validationCheck.getParameters().get(0).contains("is not the same as in the notification file: " + sessionId));

        // make sure that it will be the CRL from the snapsh
        final List<RpkiObject> objects = rpkiObjects.all().collect(Collectors.toList());
        assertEquals(1, objects.size());
        RpkiObject rpkiObject = objects.get(0);
        assertEquals(RpkiObject.Type.CRL, rpkiObject.getType());
        assertEquals(Sets.newHashSet("rsync://host/path/crl1.crl"), rpkiObject.getLocations());
    }

    @Test
    public void should_parse_notification_use_delta_and_fail_to_use_it() {
        final byte[] certificate = Objects.aParseableCertificate();
        final String sessionId = UUID.randomUUID().toString();
        final byte[] emptySnapshotXml = Objects.snapshotXml(3, sessionId);

        final Objects.SnapshotInfo emptySnapshot = new Objects.SnapshotInfo("https://host/path/snapshot.xml", Sha256.hash(emptySnapshotXml));
        rrdpClient.add(emptySnapshot.uri, emptySnapshotXml);

        final Objects.DeltaPublish publishCert = new Objects.DeltaPublish("rsync://host/path/cert1.cer", certificate);
        final byte[] deltaXml1 = Objects.deltaXml(2, sessionId, publishCert);

        // republish without hash
        final Objects.DeltaPublish republishCert = new Objects.DeltaPublish("rsync://host/path/cert2.cer", certificate);
        final byte[] deltaXml2 = Objects.deltaXml(3, sessionId, republishCert);

        final Objects.DeltaInfo deltaInfo2 = new Objects.DeltaInfo("https://host/path/delta2.xml", Sha256.hash(deltaXml1), 2);
        final Objects.DeltaInfo deltaInfo3 = new Objects.DeltaInfo("https://host/path/delta3.xml", Sha256.hash(deltaXml2), 3);
        rrdpClient.add(deltaInfo2.uri, deltaXml1);
        rrdpClient.add(deltaInfo3.uri, deltaXml2);

        final String notificationUri = "https://rrdp.ripe.net/notification.xml";
        rrdpClient.add(notificationUri, Objects.notificationXml(3, sessionId, emptySnapshot, deltaInfo2, deltaInfo3));

        final TrustAnchor trustAnchor = TestObjects.newTrustAnchor();
        entityManager.persist(trustAnchor);

        // make current serial lower to trigger delta download
        final RpkiRepository rpkiRepository = makeRpkiRepository(sessionId, notificationUri, trustAnchor);

        RrdpRepositoryValidationRun validationRun = new RrdpRepositoryValidationRun(rpkiRepository);
        subject.storeRepository(rpkiRepository, validationRun);
        assertEquals(0, validationRun.getValidationChecks().size());

        final List<RpkiObject> objects = rpkiObjects.all().collect(Collectors.toList());
        assertEquals(Sets.newHashSet("rsync://host/path/cert1.cer"), objects.get(0).getLocations());
        assertEquals(1, objects.size());
    }

    @Test
    public void should_parse_notification_use_delta_and_republish_object() {
        final byte[] certificate = Objects.aParseableCertificate();
        final String sessionId = UUID.randomUUID().toString();
        final long serial = 3;

        final Objects.Publish crl = new Objects.Publish("rsync://host/path/crl1.crl", Objects.aParseableCrl());
        rrdpClient.add(crl.uri, crl.content);

        final byte[] snapshotXml = Objects.snapshotXml(serial, sessionId, crl);
        final Objects.SnapshotInfo snapshot = new Objects.SnapshotInfo("https://host/path/snapshot.xml", Sha256.hash(snapshotXml));
        rrdpClient.add(snapshot.uri, snapshotXml);

        final Objects.DeltaPublish publishCert = new Objects.DeltaPublish("rsync://host/path/cert.cer", certificate);
        final byte[] deltaXml1 = Objects.deltaXml(2, sessionId, publishCert);

        final Objects.DeltaPublish republishCert = new Objects.DeltaPublish("rsync://host/path/cert.cer", Sha256.hash(publishCert.content), certificate);
        final byte[] deltaXml2 = Objects.deltaXml(serial, sessionId, republishCert);

        final Objects.DeltaInfo deltaInfo1 = new Objects.DeltaInfo("https://host/path/delta1.xml", Sha256.hash(deltaXml1), 2);
        final Objects.DeltaInfo deltaInfo2 = new Objects.DeltaInfo("https://host/path/delta2.xml", Sha256.hash(deltaXml2), 3);
        rrdpClient.add(deltaInfo1.uri, deltaXml1);
        rrdpClient.add(deltaInfo2.uri, deltaXml2);

        final String notificationUri = "https://rrdp.ripe.net/notification.xml";
        rrdpClient.add(notificationUri, Objects.notificationXml(3, sessionId, snapshot, deltaInfo1, deltaInfo2));

        final TrustAnchor trustAnchor = TestObjects.newTrustAnchor();
        entityManager.persist(trustAnchor);

        // make current serial lower to trigger delta download
        final RpkiRepository rpkiRepository = makeRpkiRepository(sessionId, notificationUri, trustAnchor);

        // do the first run to get the snapshot
        RrdpRepositoryValidationRun validationRun = new RrdpRepositoryValidationRun(rpkiRepository);
        subject.storeRepository(rpkiRepository, validationRun);
        assertEquals(0, validationRun.getValidationChecks().size());

        final List<RpkiObject> objects = rpkiObjects.all().collect(Collectors.toList());
        assertEquals(1, objects.size());
    }


    @Test
    public void should_parse_notification_use_delta_non_contiguous_delta_fallback_to_snapshot() {
        final byte[] certificate = Objects.aParseableCertificate();
        final String sessionId = UUID.randomUUID().toString();
        final Objects.Publish crl = new Objects.Publish("rsync://host/path/crl1.crl", Objects.aParseableCrl());
        rrdpClient.add(crl.uri, crl.content);

        final byte[] snapshotXml = Objects.snapshotXml(3, sessionId, crl);

        final Objects.SnapshotInfo emptySnapshot = new Objects.SnapshotInfo("https://host/path/snapshot.xml", Sha256.hash(snapshotXml));
        rrdpClient.add(emptySnapshot.uri, snapshotXml);

        final Objects.DeltaPublish publishCert = new Objects.DeltaPublish("rsync://host/path/cert.cer", certificate);
        final byte[] deltaXml1 = Objects.deltaXml(2, sessionId, publishCert);

        final Objects.DeltaPublish republishCert = new Objects.DeltaPublish("rsync://host/path/cert.cer", Sha256.hash(publishCert.content), certificate);
        final byte[] deltaXml2 = Objects.deltaXml(4, sessionId, republishCert);

        final Objects.DeltaInfo deltaInfo1 = new Objects.DeltaInfo("https://host/path/delta1.xml", Sha256.hash(deltaXml1), 2);
        final Objects.DeltaInfo deltaInfo2 = new Objects.DeltaInfo("https://host/path/delta2.xml", Sha256.hash(deltaXml2), 4);
        rrdpClient.add(deltaInfo1.uri, deltaXml1);
        rrdpClient.add(deltaInfo2.uri, deltaXml2);

        final String notificationUri = "https://rrdp.ripe.net/notification.xml";
        rrdpClient.add(notificationUri, Objects.notificationXml(4, sessionId, emptySnapshot, deltaInfo1, deltaInfo2));

        final TrustAnchor trustAnchor = TestObjects.newTrustAnchor();
        entityManager.persist(trustAnchor);

        // make current serial lower to trigger delta download
        final RpkiRepository rpkiRepository = makeRpkiRepository(sessionId, notificationUri, trustAnchor);

        // do the first run to get the snapshot
        final RrdpRepositoryValidationRun validationRun = new RrdpRepositoryValidationRun(rpkiRepository);
        subject.storeRepository(rpkiRepository, validationRun);
        assertEquals(1, validationRun.getValidationChecks().size());

        final ValidationCheck validationCheck = validationRun.getValidationChecks().get(0);
        assertEquals(ErrorCodes.RRDP_SERIAL_MISMATCH, validationCheck.getKey());
        assertEquals(ValidationCheck.Status.WARNING, validationCheck.getStatus());
        assertEquals(rpkiRepository.getRrdpNotifyUri(), validationCheck.getLocation());
        assertEquals("Serials of the deltas are not contiguous: found 2 and 4 after it", validationCheck.getParameters().get(0));

        final List<RpkiObject> objects = rpkiObjects.all().collect(Collectors.toList());
        assertEquals(1, objects.size());

        final RpkiObject rpkiObject = objects.get(0);
        assertEquals(RpkiObject.Type.CRL, rpkiObject.getType());
        assertEquals(Sets.newHashSet("rsync://host/path/crl1.crl"), rpkiObject.getLocations());
    }

    @Test
    public void should_parse_notification_use_delta_the_last_delta_serial_is_not_matching_fallback_to_snapshot() {
        final byte[] certificate = Objects.aParseableCertificate();
        final String sessionId = UUID.randomUUID().toString();
        final Objects.Publish crl = new Objects.Publish("rsync://host/path/crl1.crl", Objects.aParseableCrl());
        rrdpClient.add(crl.uri, crl.content);

        final byte[] snapshotXml = Objects.snapshotXml(4, sessionId, crl);

        final Objects.SnapshotInfo emptySnapshot = new Objects.SnapshotInfo("https://host/path/snapshot.xml", Sha256.hash(snapshotXml));
        rrdpClient.add(emptySnapshot.uri, snapshotXml);

        final Objects.DeltaPublish publishCert = new Objects.DeltaPublish("rsync://host/path/cert.cer", certificate);
        final byte[] deltaXml1 = Objects.deltaXml(2, sessionId, publishCert);

        final Objects.DeltaPublish republishCert = new Objects.DeltaPublish("rsync://host/path/cert.cer", Sha256.hash(publishCert.content), certificate);
        final byte[] deltaXml2 = Objects.deltaXml(3, sessionId, republishCert);

        final Objects.DeltaInfo deltaInfo1 = new Objects.DeltaInfo("https://host/path/delta1.xml", Sha256.hash(deltaXml1), 2);
        final Objects.DeltaInfo deltaInfo2 = new Objects.DeltaInfo("https://host/path/delta2.xml", Sha256.hash(deltaXml2), 3);
        rrdpClient.add(deltaInfo1.uri, deltaXml1);
        rrdpClient.add(deltaInfo2.uri, deltaXml2);

        final String notificationUri = "https://rrdp.ripe.net/notification.xml";
        rrdpClient.add(notificationUri, Objects.notificationXml(4, sessionId, emptySnapshot, deltaInfo1, deltaInfo2));

        final TrustAnchor trustAnchor = TestObjects.newTrustAnchor();
        entityManager.persist(trustAnchor);

        // make current serial lower to trigger delta download
        final RpkiRepository rpkiRepository = makeRpkiRepository(sessionId, notificationUri, trustAnchor);

        // do the first run to get the snapshot
        final RrdpRepositoryValidationRun validationRun = new RrdpRepositoryValidationRun(rpkiRepository);
        subject.storeRepository(rpkiRepository, validationRun);
        assertEquals(1, validationRun.getValidationChecks().size());

        final ValidationCheck validationCheck = validationRun.getValidationChecks().get(0);
        assertEquals(ErrorCodes.RRDP_SERIAL_MISMATCH, validationCheck.getKey());
        assertEquals(ValidationCheck.Status.WARNING, validationCheck.getStatus());
        assertEquals(rpkiRepository.getRrdpNotifyUri(), validationCheck.getLocation());
        assertEquals("The last delta serial is 3, notification file serial is 4", validationCheck.getParameters().get(0));

        final List<RpkiObject> objects = rpkiObjects.all().collect(Collectors.toList());
        assertEquals(1, objects.size());

        final RpkiObject rpkiObject = objects.get(0);
        assertEquals(RpkiObject.Type.CRL, rpkiObject.getType());
        assertEquals(Sets.newHashSet("rsync://host/path/crl1.crl"), rpkiObject.getLocations());
    }

    @Test
    public void should_parse_notification_use_delta_mismatching_delta_hash_fallback_to_snapshot() {
        final byte[] certificate = Objects.aParseableCertificate();
        final String sessionId = UUID.randomUUID().toString();
        final Objects.Publish crl = new Objects.Publish("rsync://host/path/crl1.crl", Objects.aParseableCrl());
        rrdpClient.add(crl.uri, crl.content);

        final byte[] snapshotXml = Objects.snapshotXml(3, sessionId, crl);

        final Objects.SnapshotInfo emptySnapshot = new Objects.SnapshotInfo("https://host/path/snapshot.xml", Sha256.hash(snapshotXml));
        rrdpClient.add(emptySnapshot.uri, snapshotXml);

        final Objects.DeltaPublish publishCert = new Objects.DeltaPublish("rsync://host/path/cert.cer", certificate);
        final byte[] deltaXml1 = Objects.deltaXml(3, sessionId, publishCert);

        final Objects.DeltaInfo deltaInfo1 = new Objects.DeltaInfo("https://host/path/delta1.xml", Hex.parse("FFFFFFFF"), 3);
        rrdpClient.add(deltaInfo1.uri, deltaXml1);

        final String notificationUri = "https://rrdp.ripe.net/notification.xml";
        rrdpClient.add(notificationUri, Objects.notificationXml(3, sessionId, emptySnapshot, deltaInfo1));

        final TrustAnchor trustAnchor = TestObjects.newTrustAnchor();
        entityManager.persist(trustAnchor);

        // make current serial lower to trigger delta download
        final RpkiRepository rpkiRepository = makeRpkiRepository(sessionId, notificationUri, trustAnchor);

        // do the first run to get the snapshot
        RrdpRepositoryValidationRun validationRun = new RrdpRepositoryValidationRun(rpkiRepository);
        subject.storeRepository(rpkiRepository, validationRun);
        assertEquals(1, validationRun.getValidationChecks().size());
        final ValidationCheck validationCheck = validationRun.getValidationChecks().get(0);
        assertEquals(ErrorCodes.RRDP_WRONG_DELTA_HASH, validationCheck.getKey());
        assertEquals(ValidationCheck.Status.WARNING, validationCheck.getStatus());
        assertEquals(rpkiRepository.getRrdpNotifyUri(), validationCheck.getLocation());

        assertTrue(validationCheck.getParameters().get(0).startsWith("Hash of the delta file"));
        assertTrue(validationCheck.getParameters().get(0).contains("is " + Hex.format(Sha256.hash(deltaXml1)) + ", but notification file says FFFFFFFF"));

        final List<RpkiObject> objects = rpkiObjects.all().collect(Collectors.toList());
        assertEquals(1, objects.size());

        final RpkiObject rpkiObject = objects.get(0);
        assertEquals(RpkiObject.Type.CRL, rpkiObject.getType());
        assertEquals(Sets.newHashSet("rsync://host/path/crl1.crl"), rpkiObject.getLocations());
    }


    private RpkiRepository makeRpkiRepository(String sessionId, String notificationUri, TrustAnchor trustAnchor) {
        final RpkiRepository rpkiRepository = new RpkiRepository(trustAnchor, notificationUri, RpkiRepository.Type.RRDP);
        rpkiRepository.setRrdpSerial(BigInteger.valueOf(1L));
        rpkiRepository.setRrdpSessionId(sessionId);
        entityManager.persist(rpkiRepository);
        return rpkiRepository;
    }
}