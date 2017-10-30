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
import java.math.BigInteger;
import java.util.Comparator;
import java.util.Optional;


@Service
@Slf4j
public class RrdpService {

    private RrdpParser rrdpParser = new RrdpParser();

    private RrdpClient rrdpClient;

    private RpkiObjects rpkiObjectRepository;

    private String currentSessionId;
    private BigInteger currentSerial;

    @Autowired
    public RrdpService(final RrdpClient rrdpClient, final RpkiObjects rpkiObjectRepository) {
        this.rrdpClient = rrdpClient;
        this.rpkiObjectRepository = rpkiObjectRepository;
    }

    @Transactional(Transactional.TxType.REQUIRED)
    public void storeRepository(final RpkiRepository rpkiRepository, final RpkiRepositoryValidationRun validationRun) {
        final Notification notification = rrdpClient.readStream(rpkiRepository.getUri(), is -> rrdpParser.notification(is));
        if (notification.sessionId.equals(currentSessionId)) {
            if (currentSerial.compareTo(notification.serial) <= 0) {
                notification.deltas.stream().
                        filter(d -> d.getSerial().compareTo(currentSerial) > 0).
                        sorted(Comparator.comparing(DeltaInfo::getSerial)).
                        map(di -> {
                            // TODO @mpuzanov Verify delta file hash, it will break the whole
                            // nice idea of streaming, but we MUST do it according to RFC
                            final Delta d = rrdpClient.readStream(di.getUri(), i -> rrdpParser.delta(i));
                            if (!d.getSessionId().equals(notification.sessionId)) {
                                throw new RrdpException("Session id of the delta (" + di +
                                        ") is not the same as in the notification file: " + notification.sessionId);
                            }
                            if (d.getSerial().equals(currentSerial.add(BigInteger.ONE))) {
                                throw new RrdpException("Serials of the deltas (" + di + "), is not contiguous.");
                            }
                            return d;
                        }).
                        forEach(d -> {
                            storeDelta(d, validationRun);
                            currentSerial = currentSerial.add(BigInteger.ONE);
                        });
            } else {

            }
        } else {
            final Snapshot snapshot = rrdpClient.readStream(notification.snapshotUri, i -> rrdpParser.snapshot(i));
            storeSnapshot(snapshot, validationRun);
            currentSessionId = notification.sessionId;
            currentSerial = notification.serial;
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
                    RpkiObject result = maybeRpkiObject.right().value();
                    rpkiObjectRepository.add(result);
                    validationRun.objectAdded();
                    log.debug("added to database {}", result);
                    return result;
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
            rpkiObjectRepository.remove(maybeObject.get());
        } else {
            ValidationCheck validationCheck = new ValidationCheck(validationRun, uri,
                    ValidationCheck.Status.ERROR, "rrdp.withdraw.nonexistent.object", Sha256.format(deltaWithdraw.getHash()));
            validationRun.addCheck(validationCheck);
        }
    }

    private void applyDeltaPublish(RpkiRepositoryValidationRun validationRun, String uri, DeltaPublish deltaPublish) {
        if (deltaPublish.getHash().isPresent()) {
            final Optional<RpkiObject> existing = rpkiObjectRepository.findBySha256(deltaPublish.getHash().get());
            if (existing.isPresent()) {
                addRpkiObject(validationRun, uri, deltaPublish);
            } else {
                ValidationCheck validationCheck = new ValidationCheck(validationRun, uri,
                        ValidationCheck.Status.ERROR, "rrdp.replace.nonexistent.object", Sha256.format(deltaPublish.getHash().get()));
                validationRun.addCheck(validationCheck);
            }
        } else {
            addRpkiObject(validationRun, uri, deltaPublish);
        }
    }

    private void addRpkiObject(RpkiRepositoryValidationRun validationRun, String uri, DeltaPublish deltaPublish) {
        final Either<ValidationResult, RpkiObject> maybeRpkiObject = createRpkiObject(uri, deltaPublish.getContent());
        if (maybeRpkiObject.isLeft()) {
            validationRun.addChecks(maybeRpkiObject.left().value());
        } else {
            RpkiObject object = maybeRpkiObject.right().value();
            rpkiObjectRepository.add(object);
            log.debug("Added to database {}", object);
        }
    }

    Either<ValidationResult, RpkiObject> createRpkiObject(final String uri, final byte[] content) {
        ValidationResult validationResult = ValidationResult.withLocation(uri);
        CertificateRepositoryObject repositoryObject = CertificateRepositoryObjectFactory.createCertificateRepositoryObject(content, validationResult);
        if (validationResult.hasFailures()) {
            return Either.left(validationResult);
        } else {
            return Either.right(new RpkiObject(uri, repositoryObject));
        }
    }
}
