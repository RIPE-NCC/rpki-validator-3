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

import com.google.common.hash.Hashing;
import fj.data.Either;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.commons.util.RepositoryObjectType;
import net.ripe.rpki.commons.validation.ValidationResult;
import net.ripe.rpki.validator3.domain.ErrorCodes;
import net.ripe.rpki.validator3.domain.RpkiObjectUtils;
import net.ripe.rpki.validator3.domain.metrics.RrdpMetricsService;
import net.ripe.rpki.validator3.storage.Storage;
import net.ripe.rpki.validator3.storage.Tx;
import net.ripe.rpki.validator3.storage.data.RpkiObject;
import net.ripe.rpki.validator3.storage.data.RpkiRepository;
import net.ripe.rpki.validator3.storage.data.validation.RpkiRepositoryValidationRun;
import net.ripe.rpki.validator3.storage.data.validation.ValidationCheck;
import net.ripe.rpki.validator3.storage.stores.RpkiObjects;
import net.ripe.rpki.validator3.storage.stores.RpkiRepositories;
import net.ripe.rpki.validator3.util.Hex;
import net.ripe.rpki.validator3.util.Sha256;
import net.ripe.rpki.validator3.util.Time;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static net.ripe.rpki.validator3.domain.ErrorCodes.RRDP_SNAPSHOT_FETCH_LOCAL_AHEAD;
import static net.ripe.rpki.validator3.domain.ErrorCodes.RRDP_SNAPSHOT_FETCH_NEW_SESSION;


@Service
@Slf4j
@Profile("!test")
public class RrdpServiceImpl implements RrdpService {

    private static final int PENDING_OBJECT_COMMIT_BATCH_SIZE_BYTES = 1_000_000;

    private static final Predicate<RepositoryObjectType> NO_MANIFESTS_PREDICATE = (type) -> type != RepositoryObjectType.Manifest;
    private static final Predicate<RepositoryObjectType> ONLY_MANIFESTS_PREDICATE = (type) -> type == RepositoryObjectType.Manifest;

    private final RrdpParser rrdpParser = new RrdpParser();

    private final RrdpClient rrdpClient;

    private final RpkiObjects rpkiObjects;

    private final RpkiRepositories rpkiRepositories;

    private final Storage storage;

    private final ForkJoinPool rrdpProcessingPool;

    private final RrdpMetricsService rrdpMetrics;

    @Autowired
    public RrdpServiceImpl(
            @Value("${rpki.validator.rrdp.repository.parallelism:4}") int rrdpProcessingParallelism,
            final RrdpClient rrdpClient,
            final RpkiObjects rpkiObjects,
            final RpkiRepositories rpkiRepositories,
            final Storage storage,
            final RrdpMetricsService rrdpMetrics
    ) {
        this.rrdpProcessingPool = new ForkJoinPool(rrdpProcessingParallelism);
        this.rrdpClient = rrdpClient;
        this.rpkiObjects = rpkiObjects;
        this.rpkiRepositories = rpkiRepositories;
        this.storage = storage;
        this.rrdpMetrics = rrdpMetrics;
    }

    @Override
    public boolean storeRepository(final RpkiRepository rpkiRepository, final RpkiRepositoryValidationRun validationRun) {
        try {
            return doStoreRepository(rpkiRepository, validationRun);
        } catch (RrdpException e) {
            log.warn("Error retrieving RRDP repository at {}: " + e.getMessage(), rpkiRepository.getRrdpNotifyUri());
            ValidationCheck validationCheck = new ValidationCheck(rpkiRepository.getRrdpNotifyUri(),
                    ValidationCheck.Status.ERROR, ErrorCodes.RRDP_FETCH, e.getMessage());
            validationRun.addCheck(validationCheck);
            validationRun.setFailed();
        }
        return false;
    }

    private boolean doStoreRepository(RpkiRepository rpkiRepository, RpkiRepositoryValidationRun validationRun) {
        final Notification notification = rrdpClient.readStream(rpkiRepository.getRrdpNotifyUri(), rrdpParser::notification);

        log.info("Repository {}: local serial is '{}', latest serial is {}",
                rpkiRepository.getRrdpNotifyUri(), rpkiRepository.getRrdpSerial(), notification.serial);

        AtomicBoolean changedObjects = new AtomicBoolean(false);
        if (notification.sessionId.equals(rpkiRepository.getRrdpSessionId())) {
            // The RRDP session is still the same
            if (rpkiRepository.getRrdpSerial().compareTo(notification.serial) <= 0) {
                // The notification contains updates that we do not have locally
                try {
                    List<DeltaInfo> orderedDeltas = verifyAndOrderDeltaSerials(notification, rpkiRepository);
                    rrdpProcessingPool.submit(() -> orderedDeltas
                            .stream()
                            .map(di -> readDelta(notification, di))
                            .forEachOrdered(d -> storage.writeTx0(tx -> {
                                verifyDeltaIsApplicable(tx, d);
                                storeDelta(tx, d, validationRun, rpkiRepository, changedObjects);
                                tx.afterCommit(() -> rpkiRepository.setRrdpSerial(rpkiRepository.getRrdpSerial().add(BigInteger.ONE)));
                            }))
                    ).join();
                } catch (RrdpException e) {
                    log.info("Processing deltas failed {}, falling back to snapshot processing.", e.getMessage());
                    rrdpMetrics.update(rpkiRepository.getRrdpNotifyUri(), ErrorCodes.RRDP_FETCH_DELTAS);
                    final String errorCode = e.getErrorCode() != null ? e.getErrorCode() : ErrorCodes.RRDP_FETCH_DELTAS;
                    ValidationCheck validationCheck = new ValidationCheck(rpkiRepository.getRrdpNotifyUri(),
                            ValidationCheck.Status.WARNING, errorCode, e.getMessage());
                    validationRun.addCheck(validationCheck);
                    processSnapshot(rpkiRepository, validationRun, notification, changedObjects);
                }
            } else {
                // The local repository is *ahead* the snapshot (for the same session): This should not happen, fall back to snapshot.
                log.info("Repository serial {} is ahead of serial in notification file {}, fetching the snapshot",
                        rpkiRepository.getRrdpSessionId(), notification.sessionId);
                rrdpMetrics.update(rpkiRepository.getRrdpNotifyUri(), RRDP_SNAPSHOT_FETCH_LOCAL_AHEAD);
                processSnapshot(rpkiRepository, validationRun, notification, changedObjects);
            }
        } else {
            // New RRDP session: Start from snapshot
            log.info("Repository has session id '{}' but the downloaded version has session id '{}', fetching the snapshot",
                    rpkiRepository.getRrdpSessionId(), notification.sessionId);
            rrdpMetrics.update(rpkiRepository.getRrdpNotifyUri(), RRDP_SNAPSHOT_FETCH_NEW_SESSION);
            processSnapshot(rpkiRepository, validationRun, notification, changedObjects);
        }
        return changedObjects.get();
    }

    private void processSnapshot(RpkiRepository rpkiRepository, RpkiRepositoryValidationRun validationRun, Notification notification, AtomicBoolean changedObjects) {
        rrdpProcessingPool.submit(() -> {
            rrdpClient.processUsingTemporaryFile(notification.snapshotUri, Hashing.sha256(), (snapshotPath, snapshotHash) -> {
                if (!Arrays.equals(Hex.parse(notification.snapshotHash), snapshotHash.asBytes())) {
                    throw new RrdpException(ErrorCodes.RRDP_WRONG_SNAPSHOT_HASH, "Hash of the snapshot file " +
                            notification.snapshotUri + " is " + Hex.format(snapshotHash.asBytes()) + ", but notification file says " + notification.snapshotHash);
                }

                Long timedStoreSnapshot = Time.timed(() -> {
                    int counter = 0;

                    log.debug("Processing RRDP repository {} snapshot, except for manifests", rpkiRepository.getRrdpNotifyUri());
                    counter += processDownloadedSnapshot(rpkiRepository, validationRun, notification, snapshotPath, NO_MANIFESTS_PREDICATE);

                    log.debug("Processing RRDP repository {} snapshot, manifests only", rpkiRepository.getRrdpNotifyUri());
                    counter += processDownloadedSnapshot(rpkiRepository, validationRun, notification, snapshotPath, ONLY_MANIFESTS_PREDICATE);

                    storage.writeTx0(tx -> rpkiRepositories.update(tx, rpkiRepository));

                    changedObjects.set(counter > 0);

                    log.info("Added (or updated locations for) {} new objects", counter);
                });
                log.info("Storing snapshot {} time {}ms", rpkiRepository.getRrdpNotifyUri(), timedStoreSnapshot);

                return null;
            });
        }).join();
    }

    private int processDownloadedSnapshot(RpkiRepository rpkiRepository, RpkiRepositoryValidationRun validationRun, Notification notification, Path snapshotPath, Predicate<RepositoryObjectType> typePredicate) {
        try (InputStream in = new FileInputStream(snapshotPath.toFile())) {
            AtomicInteger counter = new AtomicInteger(0);
            AtomicInteger pendingObjectsBytes = new AtomicInteger(0);
            List<SnapshotObject> pendingObjects = new ArrayList<>(1000);
            Runnable commitPendingObjects = () -> {
                storage.writeTx0(tx -> counter.addAndGet(
                        storeSnapshotObjects(tx, pendingObjects, validationRun))
                );
                pendingObjects.clear();
                pendingObjectsBytes.set(0);
            };

            rrdpParser.parseSnapshot(
                    in,
                    (snapshotInfo) -> {
                        if (!notification.sessionId.equals(snapshotInfo.getSessionId())) {
                            throw new RrdpException(ErrorCodes.RRDP_WRONG_SNAPSHOT_SESSION, "Session id of the snapshot (" + snapshotInfo.getSessionId() +
                                    ") is not the same as in the notification file: " + notification.sessionId);
                        }
                        if (!notification.getSerial().equals(snapshotInfo.getSerial())) {
                            throw new RrdpException(ErrorCodes.RRDP_SERIAL_MISMATCH, "Serial of the snapshot (" + snapshotInfo.getSerial() +
                                    ") is not the same as in the notification file: " + notification.serial);
                        }

                        rpkiRepository.setRrdpSessionId(snapshotInfo.getSessionId());
                        rpkiRepository.setRrdpSerial(snapshotInfo.getSerial());
                    },
                    (snapshotObject) -> {
                        if (!typePredicate.test(RepositoryObjectType.parse(snapshotObject.getUri()))) {
                            return;
                        }

                        pendingObjects.add(snapshotObject);
                        int bytes = pendingObjectsBytes.addAndGet(snapshotObject.content.length);
                        if (bytes > PENDING_OBJECT_COMMIT_BATCH_SIZE_BYTES) {
                            commitPendingObjects.run();
                        }
                    }
            );

            commitPendingObjects.run();

            return counter.get();
        } catch (IOException e) {
            throw new RrdpException("Couldn't read snapshot: ", e);
        }
    }

    private Delta readDelta(Notification notification, DeltaInfo di) {
        final byte[] deltaBody = rrdpClient.getBody(di.getUri());
        final byte[] deltaHash = Sha256.hash(deltaBody);
        if (!Arrays.equals(Hex.parse(di.getHash()), deltaHash)) {
            throw new RrdpException(ErrorCodes.RRDP_WRONG_DELTA_HASH, "Hash of the delta file " + di + " is " + Hex.format(deltaHash) +
                    ", but notification file says " + di.getHash());
        }

        final Delta d;
        try {
            d = rrdpParser.delta(new ByteArrayInputStream(deltaBody));
        } catch (Exception e) {
            throw new RrdpException("Error parsing delta (" + di + "): " + notification.sessionId, e);
        }
        if (!d.getSessionId().equals(notification.sessionId)) {
            throw new RrdpException(ErrorCodes.RRDP_WRONG_DELTA_SESSION, "Session id of the delta (" + di +
                ") is not the same as in the notification file: " + notification.sessionId);
        }
        if (!d.getSerial().equals(di.getSerial())) {
            throw new RrdpException(ErrorCodes.RRDP_SERIAL_MISMATCH, "Serial of the delta (" + d.getSerial() +
                    ") is not the same as in the notification file: " + di);
        }
        return d;
    }

    private List<DeltaInfo> verifyAndOrderDeltaSerials(final Notification notification, RpkiRepository rpkiRepository) {
        List<DeltaInfo> orderedDeltas = notification.getDeltas().stream()
                .filter(d -> d.getSerial().compareTo(rpkiRepository.getRrdpSerial()) > 0)
                .sorted(Comparator.comparing(DeltaInfo::getSerial))
                .collect(Collectors.toList());
        if (orderedDeltas.isEmpty()) {
            if (!rpkiRepository.getRrdpSerial().equals(notification.serial)) {
                throw new RrdpException(ErrorCodes.RRDP_SERIAL_MISMATCH, "The current serial is " + rpkiRepository.getRrdpSerial() +
                        ", notification file serial is " + notification.serial + ", but the list of deltas is empty.");
            }
        } else {
            final BigInteger earliestDeltaSerial = orderedDeltas.get(0).getSerial();
            final BigInteger latestDeltaSerial = orderedDeltas.get(orderedDeltas.size() - 1).getSerial();
            if (!notification.serial.equals(latestDeltaSerial)) {
                throw new RrdpException(ErrorCodes.RRDP_SERIAL_MISMATCH, "The last delta serial is " + latestDeltaSerial +
                        ", notification file serial is " + notification.serial);
            }
            if (earliestDeltaSerial.compareTo(notification.serial) > 0) {
                throw new RrdpException(ErrorCodes.RRDP_SERIAL_MISMATCH, "The earliest available delta serial " + earliestDeltaSerial +
                        " is later than notification file serial is " + notification.serial);
            }
            if (orderedDeltas.size() > 1) {
                for (int i = 0; i < orderedDeltas.size() - 1; i++) {
                    final BigInteger currentSerial = orderedDeltas.get(i).getSerial();
                    final BigInteger nextSerial = orderedDeltas.get(i + 1).getSerial();
                    if (!currentSerial.add(BigInteger.ONE).equals(nextSerial)) {
                        throw new RrdpException(ErrorCodes.RRDP_SERIAL_MISMATCH, String.format("Serials of the deltas are not contiguous: found %d and %d after it", currentSerial, nextSerial));
                    }
                }
            }
        }
        return orderedDeltas;
    }

    int storeSnapshotObjects(final Tx.Write tx, List<SnapshotObject> snapshotObjects,
                             final RpkiRepositoryValidationRun validationRun) {
        final AtomicInteger counter = new AtomicInteger();

        // Parsing RPKI objects is CPU bound, so do this with any available threads
        snapshotObjects.parallelStream().map((value) ->
                RpkiObjectUtils.createRpkiObject(value.getUri(), value.getContent())
        ).collect(Collectors.toList()).forEach((maybeRpkiObject) ->
                storeSnapshotObject(tx, validationRun, maybeRpkiObject, counter)
        );

        return counter.get();
    }

    private void storeSnapshotObject(Tx.Write tx, RpkiRepositoryValidationRun validationRun, Either<ValidationResult, Pair<String, RpkiObject>> maybeRpkiObject, AtomicInteger counter) {
        if (maybeRpkiObject.isLeft()) {
            validationRun.addChecks(maybeRpkiObject.left().value());
        } else {
            final Pair<String, RpkiObject> p = maybeRpkiObject.right().value();
            final RpkiObject object = p.getRight();
            final String location = p.getLeft();
            rpkiObjects.put(tx, object, location);
            counter.incrementAndGet();
        }
    }

    private void storeDelta(final Tx.Write wtx,
                            final Delta delta,
                            final RpkiRepositoryValidationRun validationRun,
                            final RpkiRepository rpkiRepository,
                            AtomicBoolean changedObjectsCounter) {
        final AtomicInteger added = new AtomicInteger();
        final AtomicInteger deleted = new AtomicInteger();
        delta.asMap().forEach((uri, deltaElement) -> {
            if (deltaElement instanceof DeltaPublish) {
                if (applyDeltaPublish(validationRun, uri, (DeltaPublish) deltaElement, wtx)) {
                    added.incrementAndGet();
                    changedObjectsCounter.set(true);
                }
            } else if (deltaElement instanceof DeltaWithdraw) {
                if (applyDeltaWithdraw(validationRun, uri, (DeltaWithdraw) deltaElement, wtx)) {
                    deleted.incrementAndGet();
                    changedObjectsCounter.set(true);
                }
            }
        });
        log.info("Repository {}: added (or updated locations for) {} new objects, delete (or removed locations) for {} objects",
                rpkiRepository.getRrdpNotifyUri(), added.get(), deleted.get());
    }

    private boolean applyDeltaWithdraw(RpkiRepositoryValidationRun validationRun, String uri, DeltaWithdraw deltaWithdraw, Tx.Write tx) {
        final byte[] sha256 = deltaWithdraw.getHash();
        final Optional<RpkiObject> maybeObject = rpkiObjects.findBySha256(tx, sha256);
        if (maybeObject.isPresent()) {
            // The RpkiObjectCleanupJob will remove the object automatically. We cannot
            // remove it here in case the object was also published by another repository
            // (rsync or rrdp) or a malicious RRDP server fakes withdrawal.
            return true;
        } else {
            ValidationCheck validationCheck = new ValidationCheck(uri, ValidationCheck.Status.ERROR,
                    ErrorCodes.RRDP_WITHDRAW_NONEXISTENT_OBJECT, Hex.format(sha256));
            validationRun.addCheck(validationCheck);
        }
        return false;
    }

    private void verifyDeltaIsApplicable(Tx.Read tx, Delta d) {
        d.asMap().forEach((uri, deltaElement) -> {
                    if (deltaElement instanceof DeltaPublish) {
                        // If hash existed it means DeltaPublish is trying to replace, thus hash must already been in
                        // the snapshot!.
                        ((DeltaPublish) deltaElement).getHash().ifPresent(sha ->
                                checkObjectExists(deltaElement, ErrorCodes.RRDP_REPLACE_NONEXISTENT_OBJECT, sha, tx));
                    } else if (deltaElement instanceof DeltaWithdraw) {
                        checkObjectExists(deltaElement, ErrorCodes.RRDP_WITHDRAW_NONEXISTENT_OBJECT, ((DeltaWithdraw) deltaElement).getHash(), tx);
                    }
                }
        );
    }

    private void checkObjectExists(DeltaElement deltaElement, String errorCode, byte[] sha256, Tx.Read tx) {
        final Optional<RpkiObject> objectByHash = rpkiObjects.findBySha256(tx, sha256);
        if (!objectByHash.isPresent()) {
            throw new RrdpException(errorCode, "Couldn't find an object with location '" +
                    deltaElement.uri + "' with hash " + Hex.format(sha256));
        }
    }

    private boolean applyDeltaPublish(final RpkiRepositoryValidationRun validationRun,
                                      final String uri,
                                      final DeltaPublish deltaPublish,
                                      final Tx.Write tx) {

        if (deltaPublish.getHash().isPresent()) {
            final byte[] sha256 = deltaPublish.getHash().get();
            final Optional<RpkiObject> existing = rpkiObjects.findBySha256(tx, sha256);
            if (existing.isPresent()) {
                final byte[] content = deltaPublish.getContent();
                Either<ValidationResult, Pair<String, RpkiObject>> maybeRpkiObject =
                        RpkiObjectUtils.createRpkiObject(uri, content);
                if (maybeRpkiObject.isLeft()) {
                    validationRun.addChecks(maybeRpkiObject.left().value());
                } else {
                    final Pair<String, RpkiObject> p = maybeRpkiObject.right().value();
                    final RpkiObject object = p.getRight();
                    if (!Arrays.equals(object.getSha256(), sha256)) {
                        final String location = p.getLeft();
                        rpkiObjects.put(tx, object, location);
                        return true;
                    }
                }
            } else {
                ValidationCheck validationCheck = new ValidationCheck(uri, ValidationCheck.Status.ERROR,
                        ErrorCodes.RRDP_REPLACE_NONEXISTENT_OBJECT, Hex.format(sha256));
                validationRun.addCheck(validationCheck);
            }
        } else {
            final byte[] content = deltaPublish.getContent();
            final Either<ValidationResult, Pair<String, RpkiObject>> maybeRpkiObject =
                    RpkiObjectUtils.createRpkiObject(uri, content);
            if (maybeRpkiObject.isLeft()) {
                validationRun.addChecks(maybeRpkiObject.left().value());
            } else {
                final Pair<String, RpkiObject> p = maybeRpkiObject.right().value();
                final RpkiObject object = p.getRight();
                final Optional<RpkiObject> bySha256 = rpkiObjects.findBySha256(tx, Sha256.hash(content));
                if (!bySha256.isPresent()) {
                    rpkiObjects.put(tx, object, p.getLeft());
                    return true;
                }
            }
        }
        return false;
    }
}
