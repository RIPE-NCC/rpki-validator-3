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

import fj.data.Either;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.commons.crypto.CertificateRepositoryObject;
import net.ripe.rpki.commons.crypto.util.CertificateRepositoryObjectFactory;
import net.ripe.rpki.commons.validation.ValidationResult;
import net.ripe.rpki.validator3.domain.ErrorCodes;
import net.ripe.rpki.validator3.domain.RpkiObject;
import net.ripe.rpki.validator3.domain.RpkiObjects;
import net.ripe.rpki.validator3.domain.RpkiRepository;
import net.ripe.rpki.validator3.domain.RpkiRepositoryValidationRun;
import net.ripe.rpki.validator3.domain.ValidationCheck;
import net.ripe.rpki.validator3.util.Hex;
import net.ripe.rpki.validator3.util.Sha256;
import net.ripe.rpki.validator3.util.Time;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.io.ByteArrayInputStream;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;


@Service
@Slf4j
public class RrdpService {

    private final RrdpParser rrdpParser = new RrdpParser();

    private final RrdpClient rrdpClient;

    private final RpkiObjects rpkiObjectRepository;

    @Autowired
    public RrdpService(final RrdpClient rrdpClient, final RpkiObjects rpkiObjectRepository) {
        this.rrdpClient = rrdpClient;
        this.rpkiObjectRepository = rpkiObjectRepository;
    }

    @Transactional(Transactional.TxType.REQUIRED)
    public void storeRepository(final RpkiRepository rpkiRepository, final RpkiRepositoryValidationRun validationRun) {
        try {
            doStoreRepository(rpkiRepository, validationRun);
        } catch (RrdpException e) {
            log.warn("Error retrieving RRDP repository at {}: " + e.getMessage(), rpkiRepository.getRrdpNotifyUri());
            ValidationCheck validationCheck = new ValidationCheck(validationRun, rpkiRepository.getRrdpNotifyUri(),
                    ValidationCheck.Status.ERROR, ErrorCodes.RRDP_FETCH, e.getMessage());
            validationRun.addCheck(validationCheck);
            validationRun.setFailed();
        }
    }

    private void doStoreRepository(RpkiRepository rpkiRepository, RpkiRepositoryValidationRun validationRun) {
        final Notification notification = rrdpClient.readStream(rpkiRepository.getRrdpNotifyUri(), rrdpParser::notification);

        log.info("Repository {} has local serial is '{}' and the latest serial is {}",
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
                        verifyDeltaIsApplicable(d);
                        storeDelta(d, validationRun, rpkiRepository);
                        rpkiRepository.setRrdpSerial(rpkiRepository.getRrdpSerial().add(BigInteger.ONE));
                    });

                } catch (RrdpException e) {
                    log.info("Processing deltas failed {}, falling back to snapshot processing.", e.getMessage());
                    final String errorCode = e.getErrorCode() != null ? e.getErrorCode() : ErrorCodes.RRDP_FETCH_DELTAS;
                    ValidationCheck validationCheck = new ValidationCheck(validationRun, rpkiRepository.getRrdpNotifyUri(),
                            ValidationCheck.Status.WARNING, errorCode, e.getMessage());
                    validationRun.addCheck(validationCheck);
                    readSnapshot(rpkiRepository, validationRun, notification);
                }
            } else {
                log.info("Repository serial {} is ahead of the serial in notification file {}, fetching the snapshot",
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
        final byte[] snapshotBody = rrdpClient.getBody(notification.snapshotUri);
        Pair<byte[], Long> timed = Time.timed(() -> Sha256.hash(snapshotBody));
        final byte[] snapshotHash = timed.getLeft();
        log.info("Calculating snapshot hash time {}ms", timed.getRight());
        if (!Arrays.equals(Hex.parse(notification.snapshotHash), snapshotHash)) {
            throw new RrdpException(ErrorCodes.RRDP_WRONG_SNAPSHOT_HASH, "Hash of the snapshot file " +
                    notification.snapshotUri + " is " + Hex.format(snapshotHash) + ", but notification file says " + notification.snapshotHash);
        }

        Pair<Snapshot, Long> timedSnapshot = Time.timed(() -> rrdpParser.snapshot(new ByteArrayInputStream(snapshotBody)));
        log.info("Parsing snapshot time {}ms", timedSnapshot.getRight());
        Pair<Object, Long> timedStoreSnapshot = Time.timed(() -> {
            storeSnapshot(timedSnapshot.getLeft(), validationRun);
            return null;
        });

        log.info("Storing snapshot time {}ms", timedStoreSnapshot.getRight());
        rpkiRepository.setRrdpSessionId(notification.sessionId);
        rpkiRepository.setRrdpSerial(notification.serial);
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

    @Transactional(Transactional.TxType.REQUIRED)
    void storeSnapshot(final Snapshot snapshot, final RpkiRepositoryValidationRun validationRun) {
        final AtomicInteger counter = new AtomicInteger();
        snapshot.asMap().forEach((objUri, value) -> {
            byte[] content = value.content;
            rpkiObjectRepository.findBySha256(Sha256.hash(content)).map(existing -> {
                existing.addLocation(objUri);
                return existing;
            }).orElseGet(() -> {
                final Either<ValidationResult, RpkiObject> maybeRpkiObject = createRpkiObject(objUri, content);
                if (maybeRpkiObject.isLeft()) {
                    validationRun.addChecks(maybeRpkiObject.left().value());
                    return null;
                } else {
                    RpkiObject object = maybeRpkiObject.right().value();
                    rpkiObjectRepository.add(object);
                    validationRun.addRpkiObject(object);
                    counter.incrementAndGet();
                    return object;
                }
            });
        });
        log.info("Added (or updated locations for) {} new objects", counter.get());
    }

    @Transactional(Transactional.TxType.REQUIRED)
    void storeDelta(final Delta delta, final RpkiRepositoryValidationRun validationRun, final RpkiRepository rpkiRepository) {
        final AtomicInteger added = new AtomicInteger();
        final AtomicInteger deleted = new AtomicInteger();
        delta.asMap().forEach((uri, deltaElement) -> {
            if (deltaElement instanceof DeltaPublish) {
                if (applyDeltaPublish(validationRun, uri, (DeltaPublish) deltaElement)) {
                    added.incrementAndGet();
                }
            } else if (deltaElement instanceof DeltaWithdraw) {
                if (applyDeltaWithdraw(validationRun, uri, (DeltaWithdraw) deltaElement)) {
                    deleted.incrementAndGet();
                }
            }
        });
        log.info("Repository {}: added (or updated locations for) {} new objects, delete (or removed locations) for {} objects",
                rpkiRepository.getRrdpNotifyUri(), added.get(), deleted.get());
    }

    private boolean applyDeltaWithdraw(RpkiRepositoryValidationRun validationRun, String uri, DeltaWithdraw deltaWithdraw) {
        final byte[] sha256 = deltaWithdraw.getHash();
        final Optional<RpkiObject> maybeObject = rpkiObjectRepository.findBySha256(sha256);
        if (maybeObject.isPresent()) {
            maybeObject.get().removeLocation(uri);
            return true;
        } else {
            ValidationCheck validationCheck = new ValidationCheck(validationRun, uri,
                    ValidationCheck.Status.ERROR, ErrorCodes.RRDP_WITHDRAW_NONEXISTENT_OBJECT, Hex.format(sha256));
            validationRun.addCheck(validationCheck);
        }
        return false;
    }

    private void verifyDeltaIsApplicable(Delta d) {
        d.asMap().forEach((uri, deltaElement) -> {
                    if (deltaElement instanceof DeltaPublish) {
                        ((DeltaPublish) deltaElement).getHash().ifPresent(sha ->
                                checkObjectExists(deltaElement, ErrorCodes.RRDP_REPLACE_NONEXISTENT_OBJECT, sha));
                    } else if (deltaElement instanceof DeltaWithdraw) {
                        checkObjectExists(deltaElement, ErrorCodes.RRDP_WITHDRAW_NONEXISTENT_OBJECT, ((DeltaWithdraw) deltaElement).getHash());
                    }
                }
        );
    }

    private void checkObjectExists(DeltaElement deltaElement, String errorCode, byte[] sha) {
        final Optional<RpkiObject> objectByHash = rpkiObjectRepository.findBySha256(sha);
        if (!objectByHash.isPresent()) {
            throw new RrdpException(errorCode, "Couldn't find an object with location '" +
                    deltaElement.uri + "' with hash " + Hex.format(sha));
        }
    }

    private boolean applyDeltaPublish(RpkiRepositoryValidationRun validationRun, String uri, DeltaPublish deltaPublish) {
        if (deltaPublish.getHash().isPresent()) {
            final byte[] sha256 = deltaPublish.getHash().get();
            final Optional<RpkiObject> existing = rpkiObjectRepository.findBySha256(sha256);
            if (existing.isPresent()) {
                return addRpkiObject(validationRun, uri, deltaPublish, sha256);
            } else {
                ValidationCheck validationCheck = new ValidationCheck(validationRun, uri,
                        ValidationCheck.Status.ERROR, ErrorCodes.RRDP_REPLACE_NONEXISTENT_OBJECT, Hex.format(sha256));
                validationRun.addCheck(validationCheck);
                return false;
            }
        } else {
            return addRpkiObject(validationRun, uri, deltaPublish, null);
        }
    }

    private boolean addRpkiObject(RpkiRepositoryValidationRun validationRun, String uri, DeltaPublish deltaPublish, final byte[] existingHash) {
        final byte[] content = deltaPublish.getContent();
        final Either<ValidationResult, RpkiObject> maybeRpkiObject = createRpkiObject(uri, content);
        if (maybeRpkiObject.isLeft()) {
            validationRun.addChecks(maybeRpkiObject.left().value());
        } else {
            final RpkiObject object = maybeRpkiObject.right().value();
            if (existingHash == null) {
                final Optional<RpkiObject> bySha256 = rpkiObjectRepository.findBySha256(Sha256.hash(content));
                if (bySha256.isPresent()) {
                    log.info("The object will not be added, there's one already existing {}", object);
                } else {
                    validationRun.addRpkiObject(object);
                    rpkiObjectRepository.merge(object);
                    return true;
                }
            } else if (!Arrays.equals(object.getSha256(), existingHash)) {
                validationRun.addRpkiObject(object);
                rpkiObjectRepository.merge(object);
                return true;
            } else {
                log.info("The object added is the same {}", object);
            }
        }
        return false;
    }

    private Either<ValidationResult, RpkiObject> createRpkiObject(final String uri, final byte[] content) {
        ValidationResult validationResult = ValidationResult.withLocation(uri);
        CertificateRepositoryObject repositoryObject = CertificateRepositoryObjectFactory.createCertificateRepositoryObject(content, validationResult);
        if (validationResult.hasFailures()) {
            return Either.left(validationResult);
        } else {
            return Either.right(new RpkiObject(uri, repositoryObject));
        }
    }
}
