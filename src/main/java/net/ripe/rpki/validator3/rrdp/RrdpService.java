package net.ripe.rpki.validator3.rrdp;

import fj.data.Either;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.commons.crypto.cms.manifest.ManifestCmsParser;
import net.ripe.rpki.commons.crypto.cms.roa.RoaCmsParser;
import net.ripe.rpki.commons.crypto.crl.X509Crl;
import net.ripe.rpki.commons.crypto.x509cert.X509ResourceCertificate;
import net.ripe.rpki.commons.crypto.x509cert.X509ResourceCertificateParser;
import net.ripe.rpki.commons.validation.ValidationResult;
import net.ripe.rpki.validator3.domain.RpkiObject;
import net.ripe.rpki.validator3.domain.RpkiObjects;
import net.ripe.rpki.validator3.util.Sha256;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigInteger;

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

    public void storeRepository(final String uri) {
        final Snapshot snapshot = rrdpClient.readStream(uri, is -> {
            Notification notification = rrdpParser.notification(is);
            return rrdpClient.readStream(notification.snapshotUri, iis -> rrdpParser.snapshot(iis));
        });
        snapshot.asMap().forEach((objUri, value) -> {
            final Either<ValidationResult, RpkiObject> maybeRpkiObject = createRpkiObject(objUri, value.content);
            if (maybeRpkiObject.isLeft()) {
                // TODO @mpuzanov Do something with the error, store it somewhere
            } else {
                rpkiObjectRepository.add(maybeRpkiObject.right().value());
            }
        });
    }

    private Either<ValidationResult, RpkiObject> createRpkiObject(final String uri, final byte[] content) {
        // TODO serialNumber must be taken from the parsed object
        try {
            if (endsWith(uri, ".cer")) {
                final X509ResourceCertificateParser parser = new X509ResourceCertificateParser();
                parser.parse(uri, content);
                if (parser.isSuccess()) {
                    final X509ResourceCertificate certificate = parser.getCertificate();
                    return Either.right(makeRpkiObject(content, certificate.getSerialNumber()));
                }
                return Either.left(parser.getValidationResult());
            } else if (endsWith(uri, ".mft")) {
                final ManifestCmsParser parser = new ManifestCmsParser();
                parser.parse(uri, content);
                if (parser.isSuccess()) {
                    final X509ResourceCertificate certificate = parser.getManifestCms().getCertificate();
                    return Either.right(makeRpkiObject(content, certificate.getSerialNumber()));
                }
                return Either.left(parser.getValidationResult());
            } else if (endsWith(uri, ".crl")) {
                final X509Crl crl = new X509Crl(content);
                // TODO @mpuzanov Use proper serial number
                BigInteger serialNumber = crl.getNumber();
                return Either.right(makeRpkiObject(content, serialNumber));
            } else if (endsWith(uri, ".roa")) {
                final RoaCmsParser parser = new RoaCmsParser();
                parser.parse(uri, content);
                if (parser.isSuccess()) {
                    final X509ResourceCertificate certificate = parser.getRoaCms().getCertificate();
                    return Either.right(makeRpkiObject(content, certificate.getSerialNumber()));
                }
                return Either.left(parser.getValidationResult());
            } else if (endsWith(uri, ".gbr")) {
                // TODO @mpuzanov Support ghost busters records
            }

            return Either.left(ValidationResult.withLocation(uri).error("unknown.object", "Unknown object type"));
        } catch (IOException e) {
            log.error("Couldn't parse and store object with URI " + uri);
            return Either.left(ValidationResult.withLocation(uri).error("parsing.object", e.getMessage()));
        }
    }

    private RpkiObject makeRpkiObject(final byte[] content, final BigInteger serialNumber) throws IOException {
        // FIXME set RPKI repository
        return new RpkiObject(null, serialNumber, Sha256.hash(content), content);
    }

    private boolean endsWith(final String s, final String end) {
        if (s.length() < end.length())
            return false;
        return s.substring(s.length() - 3, s.length()).equalsIgnoreCase(end);
    }


}
