package net.ripe.rpki.validator3.rrdp;

import fj.data.Either;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.commons.crypto.CertificateRepositoryObject;
import net.ripe.rpki.commons.crypto.util.CertificateRepositoryObjectFactory;
import net.ripe.rpki.commons.validation.ValidationResult;
import net.ripe.rpki.validator3.domain.RpkiObject;
import net.ripe.rpki.validator3.domain.RpkiObjects;
import net.ripe.rpki.validator3.domain.RpkiRepository;
import net.ripe.rpki.validator3.domain.RpkiRepositoryValidationRun;
import net.ripe.rpki.validator3.domain.ValidationCheck;
import net.ripe.rpki.validator3.util.Sha256;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.io.ByteArrayInputStream;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
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
            ValidationCheck validationCheck = new ValidationCheck(validationRun, rpkiRepository.getRrdpNotifyUri(),
                    ValidationCheck.Status.ERROR, "rrdp.error", e.getMessage());
            validationRun.addCheck(validationCheck);
        }
    }

    private void doStoreRepository(RpkiRepository rpkiRepository, RpkiRepositoryValidationRun validationRun) {
        final Notification notification = rrdpClient.readStream(rpkiRepository.getRrdpNotifyUri(), rrdpParser::notification);

        log.info("The local serial is '{}' and the latest serial is {}", rpkiRepository.getRrdpSerial(), notification.serial);

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
                        storeDelta(d, validationRun);
                        rpkiRepository.setRrdpSerial(rpkiRepository.getRrdpSerial().add(BigInteger.ONE));
                    });

                } catch (RrdpException e) {
                    log.info("Processing deltas failed {}, falling back to snapshot processing.", e.getMessage());
                    ValidationCheck validationCheck = new ValidationCheck(validationRun, rpkiRepository.getRrdpNotifyUri(),
                            ValidationCheck.Status.WARNING, "rrdp.deltas.failure", e.getMessage());
                    validationRun.addCheck(validationCheck);
                    readSnapshot(rpkiRepository, validationRun, notification);
                }
            }
        } else {
            log.info("Repository has session id '{}' but the downloaded version has session id '{}', fetching the snapshot",
                    rpkiRepository.getRrdpSessionId(), notification.sessionId);
            readSnapshot(rpkiRepository, validationRun, notification);
        }
    }

    private void readSnapshot(RpkiRepository rpkiRepository, RpkiRepositoryValidationRun validationRun, Notification notification) {
        final byte[] snapshotBody = rrdpClient.getBody(notification.snapshotUri);
        final byte[] snapshotHash = Sha256.hash(snapshotBody);
        if (!Arrays.equals(Sha256.parse(notification.snapshotHash), snapshotHash)) {
            throw new RrdpException("Hash of the snapshot file " + notification.snapshotUri + " is " + Sha256.format(snapshotHash) +
                    ", but notification file says " + notification.snapshotHash);
        }

        final Snapshot snapshot = rrdpParser.snapshot(new ByteArrayInputStream(snapshotBody));
        storeSnapshot(snapshot, validationRun);
        rpkiRepository.setRrdpSessionId(notification.sessionId);
        rpkiRepository.setRrdpSerial(notification.serial);
    }

    private Delta readDelta(Notification notification, DeltaInfo di) {
        final byte[] deltaBody = rrdpClient.getBody(di.getUri());
        final byte[] deltaHash = Sha256.hash(deltaBody);
        if (!Arrays.equals(Sha256.parse(di.getHash()), deltaHash)) {
            throw new RrdpException("Hash of the delta file " + di + " is " + Sha256.format(deltaHash) +
                    ", but notification file says " + di.getHash());
        }

        final Delta d = rrdpParser.delta(new ByteArrayInputStream(deltaBody));
        if (!d.getSessionId().equals(notification.sessionId)) {
            throw new RrdpException("Session id of the delta (" + di +
                    ") is not the same as in the notification file: " + notification.sessionId);
        }
        return d;
    }

    private void verifyDeltaSerials(final List<Delta> orderedDeltas, final Notification notification, RpkiRepository rpkiRepository) {
        if (orderedDeltas.isEmpty()) {
            if (!rpkiRepository.getRrdpSerial().equals(notification.serial)) {
                throw new RrdpException("The current serial is " + rpkiRepository.getRrdpSerial() +
                        ", notification file serial is " + notification.serial + ", but the list of deltas is empty.");
            }
        } else {
            final BigInteger lastDeltaSerial = orderedDeltas.get(orderedDeltas.size() - 1).getSerial();
            if (!notification.serial.equals(lastDeltaSerial)) {
                throw new RrdpException("The last delta serial is " + lastDeltaSerial +
                        ", notification file serial is " + notification.serial);
            }
            final BigInteger[] previous = {null};
            orderedDeltas.forEach(d -> {
                        if (previous[0] == null) {
                            previous[0] = d.getSerial();
                        } else {
                            if (!d.getSerial().equals(previous[0].add(BigInteger.ONE))) {
                                throw new RrdpException(String.format("Serials of the deltas are not contiguous: found %d and %d after it", previous[0], d.getSerial()));
                            }
                        }
                    }
            );
        }
    }

    void storeSnapshot(final Snapshot snapshot, final RpkiRepositoryValidationRun validationRun) {
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
                    log.debug("added to database {}", object);
                    return object;
                }
            });
        });
    }

    void storeDelta(final Delta delta, final RpkiRepositoryValidationRun validationRun) {
        delta.asMap().forEach((uri, deltaElement) -> {
            if (deltaElement instanceof DeltaPublish) {
                applyDeltaPublish(validationRun, uri, (DeltaPublish) deltaElement);
            } else if (deltaElement instanceof DeltaWithdraw) {
                applyDeltaWithdraw(validationRun, uri, (DeltaWithdraw) deltaElement);
            }
        });
    }

    private void applyDeltaWithdraw(RpkiRepositoryValidationRun validationRun, String uri, DeltaWithdraw deltaWithdraw) {
        final Optional<RpkiObject> maybeObject = rpkiObjectRepository.findBySha256(deltaWithdraw.getHash());
        if (maybeObject.isPresent()) {
            maybeObject.get().removeLocation(uri);
        } else {
            ValidationCheck validationCheck = new ValidationCheck(validationRun, uri,
                    ValidationCheck.Status.ERROR, "rrdp.withdraw.nonexistent.object", Sha256.format(deltaWithdraw.getHash()));
            validationRun.addCheck(validationCheck);
        }
    }

    private void applyDeltaPublish(RpkiRepositoryValidationRun validationRun, String uri, DeltaPublish deltaPublish) {
        if (deltaPublish.getHash().isPresent()) {
            final byte[] sha256 = deltaPublish.getHash().get();
            final Optional<RpkiObject> existing = rpkiObjectRepository.findBySha256(sha256);
            if (existing.isPresent()) {
                addRpkiObject(validationRun, uri, deltaPublish, sha256);
            } else {
                ValidationCheck validationCheck = new ValidationCheck(validationRun, uri,
                        ValidationCheck.Status.ERROR, "rrdp.replace.nonexistent.object", Sha256.format(sha256));
                validationRun.addCheck(validationCheck);
            }
        } else {
            addRpkiObject(validationRun, uri, deltaPublish, null);
        }
    }

    private void addRpkiObject(RpkiRepositoryValidationRun validationRun, String uri, DeltaPublish deltaPublish, final byte[] existingHash) {
        final Either<ValidationResult, RpkiObject> maybeRpkiObject = createRpkiObject(uri, deltaPublish.getContent());
        if (maybeRpkiObject.isLeft()) {
            validationRun.addChecks(maybeRpkiObject.left().value());
        } else {
            RpkiObject object = maybeRpkiObject.right().value();
            if (existingHash == null || !Arrays.equals(object.getSha256(), existingHash)) {
                validationRun.addRpkiObject(object);
                rpkiObjectRepository.add(object);
            } else {
                log.debug("The object added is the same {}", object);
            }
            log.debug("Added to database {}", object);
        }
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
