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
import javax.validation.constraints.NotNull;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Optional;


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
        if (notification.sessionId.equals(rpkiRepository.getRrdpSessionId())) {
            if (rpkiRepository.getRrdpSerial().compareTo(notification.serial) <= 0) {
                notification.deltas.stream().
                        filter(d -> d.getSerial().compareTo(rpkiRepository.getRrdpSerial()) > 0).
                        sorted(Comparator.comparing(DeltaInfo::getSerial)).
                        forEach(di -> {
                            // TODO @mpuzanov Verify delta file hash, it will break the whole
                            // nice idea of streaming, but we MUST do it according to RFC
                            final Delta d = rrdpClient.readStream(di.getUri(), rrdpParser::delta);
                            if (!d.getSessionId().equals(notification.sessionId)) {
                                throw new RrdpException("Session id of the delta (" + di +
                                        ") is not the same as in the notification file: " + notification.sessionId);
                            }
                            if (!d.getSerial().equals(rpkiRepository.getRrdpSerial().add(BigInteger.ONE))) {
                                throw new RrdpException("Serials of the deltas (" + di + "), is not contiguous.");
                            }
                            storeDelta(d, validationRun);
                            rpkiRepository.setRrdpSerial(rpkiRepository.getRrdpSerial().add(BigInteger.ONE));
                        });
            }
        } else {
            final Snapshot snapshot = rrdpClient.readStream(notification.snapshotUri, rrdpParser::snapshot);
            storeSnapshot(snapshot, validationRun);
            rpkiRepository.setRrdpSessionId(notification.sessionId);
            rpkiRepository.setRrdpSerial(notification.serial);
        }
    }

    void storeSnapshot(final Snapshot snapshot, final RpkiRepositoryValidationRun validationRun) {
        snapshot.asMap().forEach((objUri, value) -> {
            rpkiObjectRepository.findBySha256(Sha256.hash(value.content)).map(existing -> {
                existing.addLocation(objUri);
                return existing;
            }).orElseGet(() -> {
                final Either<ValidationResult, RpkiObject> maybeRpkiObject = createRpkiObject(objUri, value.content);
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
