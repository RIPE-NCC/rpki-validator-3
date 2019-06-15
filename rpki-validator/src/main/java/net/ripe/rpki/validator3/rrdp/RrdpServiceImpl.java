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
import com.google.common.hash.HashingInputStream;
import fj.data.Either;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.commons.validation.ValidationResult;
import net.ripe.rpki.validator3.domain.ErrorCodes;
import net.ripe.rpki.validator3.domain.RpkiObjectUtils;
import net.ripe.rpki.validator3.storage.Tx;
import net.ripe.rpki.validator3.storage.data.RpkiObject;
import net.ripe.rpki.validator3.storage.data.RpkiRepository;
import net.ripe.rpki.validator3.storage.data.validation.RpkiRepositoryValidationRun;
import net.ripe.rpki.validator3.storage.data.validation.ValidationCheck;
import net.ripe.rpki.validator3.storage.Storage;
import net.ripe.rpki.validator3.storage.stores.RpkiObjects;
import net.ripe.rpki.validator3.storage.stores.RpkiRepositories;
import net.ripe.rpki.validator3.storage.stores.ValidationRuns;
import net.ripe.rpki.validator3.util.Hex;
import net.ripe.rpki.validator3.util.Sha256;
import net.ripe.rpki.validator3.util.Time;
import org.apache.commons.lang3.tuple.Pair;
import javax.inject.Inject;
import org.springframework.context.annotation.Profile;
import javax.inject.Singleton;

import java.io.ByteArrayInputStream;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;


@Singleton
@Slf4j
@Profile("!test")
public class RrdpServiceImpl implements RrdpService {

    private final RrdpParser rrdpParser = new RrdpParser();

    private final RrdpClient rrdpClient;

    private final RpkiObjects rpkiObjects;

    private final RpkiRepositories rpkiRepositories;

    private final ValidationRuns validationRuns;

    private final Storage storage;

    @Inject
    public RrdpServiceImpl(final RrdpClient rrdpClient,
                           final RpkiObjects rpkiObjects,
                           final RpkiRepositories rpkiRepositories,
                           final ValidationRuns validationRuns,
                           final Storage storage) {
        this.rrdpClient = rrdpClient;
        this.rpkiObjects = rpkiObjects;
        this.rpkiRepositories = rpkiRepositories;
        this.validationRuns = validationRuns;
        this.storage = storage;
    }

    @Override
    public void storeRepository(final RpkiRepository rpkiRepository, final RpkiRepositoryValidationRun validationRun) {
        try {
            doStoreRepository(rpkiRepository, validationRun);
        } catch (RrdpException e) {
            log.warn("Error retrieving RRDP repository at {}: " + e.getMessage(), rpkiRepository.getRrdpNotifyUri());
            ValidationCheck validationCheck = new ValidationCheck(rpkiRepository.getRrdpNotifyUri(),
                    ValidationCheck.Status.ERROR, ErrorCodes.RRDP_FETCH, e.getMessage());
            validationRun.addCheck(validationCheck);
            validationRun.setFailed();
        }
    }

    private void doStoreRepository(RpkiRepository rpkiRepository, RpkiRepositoryValidationRun validationRun) {
        final Notification notification = rrdpClient.readStream(rpkiRepository.getRrdpNotifyUri(), rrdpParser::notification);

        log.info("Repository {}: local serial is '{}', latest serial is {}",
                rpkiRepository.getRrdpNotifyUri(), rpkiRepository.getRrdpSerial(), notification.serial);

        if (notification.sessionId.equals(rpkiRepository.getRrdpSessionId())) {
            if (rpkiRepository.getRrdpSerial().compareTo(notification.serial) <= 0) {
                try {
                    final List<Delta> deltas = notification.deltas.parallelStream().
                            filter(d -> d.getSerial().compareTo(rpkiRepository.getRrdpSerial()) > 0).
                            sorted(Comparator.comparing(DeltaInfo::getSerial)).
                            map(di -> readDelta(notification, di)).
                            collect(Collectors.toList());

                    verifyDeltaSerials(deltas, notification, rpkiRepository);

                    deltas.forEach(d -> {
                        storage.readTx0(tx -> verifyDeltaIsApplicable(tx, d));
                        storage.writeTx0(tx -> {
                            storeDelta(tx, d, validationRun, rpkiRepository);
                            tx.afterCommit(() -> rpkiRepository.setRrdpSerial(rpkiRepository.getRrdpSerial().add(BigInteger.ONE)));
                        });
                    });

                } catch (RrdpException e) {
                    log.info("Processing deltas failed {}, falling back to snapshot processing.", e.getMessage());
                    final String errorCode = e.getErrorCode() != null ? e.getErrorCode() : ErrorCodes.RRDP_FETCH_DELTAS;
                    ValidationCheck validationCheck = new ValidationCheck(rpkiRepository.getRrdpNotifyUri(),
                            ValidationCheck.Status.WARNING, errorCode, e.getMessage());
                    validationRun.addCheck(validationCheck);
                    readSnapshot(rpkiRepository, validationRun, notification);
                }
            } else {
                log.info("Repository serial {} is ahead of serial in notification file {}, fetching the snapshot",
                        rpkiRepository.getRrdpSessionId(), notification.sessionId);
                readSnapshot(rpkiRepository, validationRun, notification);
            }
        } else {
            log.info("Repository has session id '{}' but the downloaded version has session id '{}', fetching the snapshot",
                    rpkiRepository.getRrdpSessionId(), notification.sessionId);
            readSnapshot(rpkiRepository, validationRun, notification);
        }
    }

    private void readSnapshot(RpkiRepository rpkiRepository, RpkiRepositoryValidationRun validationRun, Notification notification) {
        final AtomicReference<HashingInputStream> hashingStream = new AtomicReference<>();
        final Pair<Snapshot, Long> timedSnapshot = Time.timed(() ->
                rrdpClient.readStream(notification.snapshotUri, is -> {
                    hashingStream.set(new HashingInputStream(Hashing.sha256(), is));
                    return rrdpParser.snapshot(hashingStream.get());
                }));

        final byte[] snapshotHash = hashingStream.get().hash().asBytes();
        if (!Arrays.equals(Hex.parse(notification.snapshotHash), snapshotHash)) {
            throw new RrdpException(ErrorCodes.RRDP_WRONG_SNAPSHOT_HASH, "Hash of the snapshot file " +
                    notification.snapshotUri + " is " + Hex.format(snapshotHash) + ", but notification file says " + notification.snapshotHash);
        }

        log.info("Downloading/hashing/parsing snapshot time {}ms", timedSnapshot.getRight());
        Long timedStoreSnapshot = Time.timed(() ->
                storage.writeTx0(tx -> {
                    storeSnapshot(tx, timedSnapshot.getLeft(), validationRun);
                    rpkiRepository.setRrdpSessionId(notification.sessionId);
                    rpkiRepository.setRrdpSerial(notification.serial);
                    rpkiRepositories.update(tx, rpkiRepository);
                }));

        log.info("Storing snapshot time {}ms", timedStoreSnapshot);
    }

    private Delta readDelta(Notification notification, DeltaInfo di) {
        final byte[] deltaBody = rrdpClient.getBody(di.getUri());
        final byte[] deltaHash = Sha256.hash(deltaBody);
        if (!Arrays.equals(Hex.parse(di.getHash()), deltaHash)) {
            throw new RrdpException(ErrorCodes.RRDP_WRONG_DELTA_HASH, "Hash of the delta file " + di + " is " + Hex.format(deltaHash) +
                    ", but notification file says " + di.getHash());
        }

        final Delta d = rrdpParser.delta(new ByteArrayInputStream(deltaBody));
        if (!d.getSessionId().equals(notification.sessionId)) {
            throw new RrdpException(ErrorCodes.RRDP_WRONG_DELTA_SESSION, "Session id of the delta (" + di +
                    ") is not the same as in the notification file: " + notification.sessionId);
        }
        return d;
    }

    private void verifyDeltaSerials(final List<Delta> orderedDeltas, final Notification notification, RpkiRepository rpkiRepository) {
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
    }

    /*
      Creating objects from byte arrays is CPU-bound, so we do it
      in parallel to make the writing transaction as short as possible.
     */
    private final ExecutorCompletionService<Either<ValidationResult, Pair<String, RpkiObject>>> asyncCreateObjects =
            new ExecutorCompletionService<>(Executors.newFixedThreadPool(Math.max(1, Runtime.getRuntime().availableProcessors() - 1)));

    void storeSnapshot(final Tx.Write tx, final Snapshot snapshot, final RpkiRepositoryValidationRun validationRun) {
        final AtomicInteger counter = new AtomicInteger();
        final AtomicInteger workCounter = new AtomicInteger(0);
        final int threshold = 10;
        snapshot.asMap().forEach((uri, value) -> {
            byte[] content = value.content;
            final byte[] sha256 = Sha256.hash(content);
            Optional<RpkiObject> existing = rpkiObjects.findBySha256(tx, sha256);
            if (existing.isPresent()) {
                rpkiObjects.addLocation(tx, existing.get().key(), uri);
                validationRuns.associate(tx, validationRun, existing.get());
            } else {
                if (workCounter.get() > threshold) {
                    storeSnapshotObject(tx, validationRun, counter);
                    workCounter.decrementAndGet();
                }
                asyncCreateObjects.submit(() -> RpkiObjectUtils.createRpkiObject(uri, content));
                workCounter.incrementAndGet();
            }
        });

        while (workCounter.getAndDecrement() > 0) {
            storeSnapshotObject(tx, validationRun, counter);
        }

        log.info("Added (or updated locations for) {} new objects", counter.get());
    }

    private void storeSnapshotObject(Tx.Write tx, RpkiRepositoryValidationRun validationRun, AtomicInteger counter) {
        try {
            final Either<ValidationResult, Pair<String, RpkiObject>> maybeRpkiObject = asyncCreateObjects.take().get();
            if (maybeRpkiObject.isLeft()) {
                validationRun.addChecks(maybeRpkiObject.left().value());
            } else {
                final Pair<String, RpkiObject> p = maybeRpkiObject.right().value();
                final RpkiObject object = p.getRight();
                final String location = p.getLeft();
                rpkiObjects.put(tx, object, location);
                validationRuns.associate(tx, validationRun, object);
                counter.incrementAndGet();
            }
        } catch (Exception e) {
            log.error("Something strange happened here", e);
        }
    }

    private void storeDelta(final Tx.Write wtx,
                            final Delta delta,
                            final RpkiRepositoryValidationRun validationRun,
                            final RpkiRepository rpkiRepository) {
        final AtomicInteger added = new AtomicInteger();
        final AtomicInteger deleted = new AtomicInteger();
        delta.asMap().forEach((uri, deltaElement) -> {
            if (deltaElement instanceof DeltaPublish) {
                if (applyDeltaPublish(validationRun, uri, (DeltaPublish) deltaElement, wtx)) {
                    added.incrementAndGet();
                }
            } else if (deltaElement instanceof DeltaWithdraw) {
                if (applyDeltaWithdraw(validationRun, uri, (DeltaWithdraw) deltaElement, wtx)) {
                    deleted.incrementAndGet();
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
            rpkiObjects.deleteLocation(tx, maybeObject.get().key(), uri);
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
                        validationRuns.associate(tx, validationRun, object);
                        return true;
                    } else {
                        log.debug("The object added is the same {}", object);
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
                if (bySha256.isPresent()) {
                    log.debug("The object will not be added, there's one already existing {}", object);
                } else {
                    final String location = p.getLeft();
                    rpkiObjects.put(tx, object, location);
                    validationRuns.associate(tx, validationRun, object);
                    return true;
                }
            }
        }
        return false;
    }
}
