package net.ripe.rpki.validator3.rrdp;

import fj.data.Either;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.commons.crypto.CertificateRepositoryObject;
import net.ripe.rpki.commons.crypto.util.CertificateRepositoryObjectFactory;
import net.ripe.rpki.commons.validation.ValidationResult;
import net.ripe.rpki.validator3.domain.RpkiObject;
import net.ripe.rpki.validator3.domain.RpkiObjects;
import net.ripe.rpki.validator3.domain.RpkiRepository;
import net.ripe.rpki.validator3.util.Sha256;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.io.IOException;

@Service
@Slf4j
public class RrdpService {

    private RrdpParser rrdpParser = new RrdpParser();

    private RrdpClient rrdpClient;

    private RpkiObjects rpkiObjectRepository;

    @Autowired
    public RrdpService(final RrdpClient rrdpClient, final RpkiObjects rpkiObjectRepository) {
        this.rrdpClient = rrdpClient;
        this.rpkiObjectRepository = rpkiObjectRepository;
    }

    @Transactional(Transactional.TxType.REQUIRED)
    public void storeRepository(final RpkiRepository rpkiRepository) {
        final String uri = rpkiRepository.getUri();
        final Snapshot snapshot = rrdpClient.readStream(uri, is -> {
            final Notification notification = rrdpParser.notification(is);
            return rrdpClient.readStream(notification.snapshotUri, i -> rrdpParser.snapshot(i));
        });
        storeSnapshot(rpkiRepository, snapshot);
    }

    void storeSnapshot(RpkiRepository rpkiRepository, Snapshot snapshot) {
        snapshot.asMap().forEach((objUri, value) -> {
            try {
                RpkiObject rpkiObject = rpkiObjectRepository.findBySha256(Sha256.hash(value.content)).orElseGet(() -> {
                    final Either<ValidationResult, RpkiObject> maybeRpkiObject = createRpkiObject(objUri, value.content);
                    if (maybeRpkiObject.isLeft()) {
                        // TODO @mpuzanov Do something with the error, store it somewhere
                        return null;
                    } else {
                        return maybeRpkiObject.right().value();
                    }
                });
                if (rpkiObject != null) {
                    rpkiObject.addLocation(objUri);
                    rpkiObjectRepository.add(rpkiObject);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    Either<ValidationResult, RpkiObject> createRpkiObject(final String uri, final byte[] content) {
        // TODO serialNumber must be taken from the parsed object
        try {
            ValidationResult validationResult = ValidationResult.withLocation(uri);
            CertificateRepositoryObject repositoryObject = CertificateRepositoryObjectFactory.createCertificateRepositoryObject(content, validationResult);
            if (validationResult.hasFailures()) {
                return Either.left(validationResult);
            } else {
                RpkiObject rpkiObject = makeRpkiObject(repositoryObject);
                rpkiObject.addLocation(uri);
                return Either.right(rpkiObject);
            }
        } catch (IOException e) {
            log.error("Couldn't parse and store object with URI " + uri);
            return Either.left(ValidationResult.withLocation(uri).error("parsing.object", e.getMessage()));
        }
    }

    private RpkiObject makeRpkiObject(CertificateRepositoryObject object) throws IOException {
        return new RpkiObject(object);
    }
}
