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
import net.ripe.rpki.validator3.util.Sha256;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.math.BigInteger;


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
            notification.deltas.stream().filter(d -> d.getSerial().compareTo(currentSerial) > 0).forEach(d -> {

            });
        } else {
            final Snapshot snapshot = rrdpClient.readStream(notification.snapshotUri, i -> rrdpParser.snapshot(i));
            storeSnapshot(snapshot, validationRun);
            currentSessionId = notification.sessionId;
            currentSerial = notification.serial;
        }
    }

    private Snapshot getSnapshot(final String uri) {
        return rrdpClient.readStream(uri, is -> {
            final Notification notification = rrdpParser.notification(is);
            return rrdpClient.readStream(notification.snapshotUri, i -> rrdpParser.snapshot(i));
        });
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
                    log.debug("added to database {}", result);
                    return result;
                }
            });
        });
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
