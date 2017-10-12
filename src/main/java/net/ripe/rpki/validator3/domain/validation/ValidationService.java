package net.ripe.rpki.validator3.domain.validation;

import com.google.common.io.Files;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.commons.crypto.CertificateRepositoryObject;
import net.ripe.rpki.commons.crypto.util.CertificateRepositoryObjectFactory;
import net.ripe.rpki.commons.crypto.x509cert.X509ResourceCertificate;
import net.ripe.rpki.commons.rsync.CommandExecutionException;
import net.ripe.rpki.commons.rsync.Rsync;
import net.ripe.rpki.commons.validation.ValidationResult;
import net.ripe.rpki.validator3.domain.*;
import net.ripe.rpki.validator3.util.Sha256;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.io.File;
import java.io.IOException;
import java.net.URI;

@Service
@Slf4j
public class ValidationService {

    private static final int DEFAULT_RSYNC_PORT = 873;

    private final TrustAnchorRepository trustAnchorRepository;
    private final RpkiObjectRepository rpkiObjectRepository;
    private final ValidationRunRepository validationRunRepository;
    private final File localRsyncStorageDirectory;

    public ValidationService(
        TrustAnchorRepository trustAnchorRepository,
        RpkiObjectRepository rpkiObjectRepository,
        ValidationRunRepository validationRunRepository,
        @Value("${rpki.validator.local.rsync.storage.directory}") File localRsyncStorageDirectory
    ) {
        this.trustAnchorRepository = trustAnchorRepository;
        this.rpkiObjectRepository = rpkiObjectRepository;
        this.validationRunRepository = validationRunRepository;
        this.localRsyncStorageDirectory = localRsyncStorageDirectory;
    }

    @Transactional(Transactional.TxType.REQUIRED)
    public void validate(long trustAnchorId) {
        TrustAnchor trustAnchor = trustAnchorRepository.get(trustAnchorId);

        log.debug("trust anchor {} located at {} with subject public key info {}", trustAnchor.getName(), trustAnchor.getLocations(), trustAnchor.getSubjectPublicKeyInfo());

        ValidationRun validationRun = new ValidationRun(trustAnchor);
        validationRunRepository.add(validationRun);

        try {
            URI trustAnchorCertificateURI = URI.create(validationRun.getTrustAnchorCertificateURI()).normalize();
            File targetFile = fetchTrustAnchorCertificate(trustAnchor, trustAnchorCertificateURI);

            ValidationResult validationResult = ValidationResult.withLocation(trustAnchorCertificateURI);
            long trustAnchorCertificateSize = targetFile.length();

            if (trustAnchorCertificateSize == 0L) {
                validationResult.error("repository.object.empty");
            } else if (trustAnchorCertificateSize > RpkiObject.MAX_SIZE) {
                validationResult.error("repository.object.too.large", String.valueOf(trustAnchorCertificateSize), String.valueOf(RpkiObject.MAX_SIZE));
            } else {
                RpkiObject rpkiObject = null;

                CertificateRepositoryObject trustAnchorCertificate = CertificateRepositoryObjectFactory.createCertificateRepositoryObject(Files.toByteArray(targetFile), validationResult);
                if (trustAnchorCertificate instanceof X509ResourceCertificate) {
                    byte[] sha256 = Sha256.hash(trustAnchorCertificate.getEncoded());

                    rpkiObject = rpkiObjectRepository.findBySha256(sha256).orElseGet(() -> {
                        X509ResourceCertificate certificate = (X509ResourceCertificate) trustAnchorCertificate;
                        return new RpkiObject(trustAnchor.getLocations(), certificate.getSerialNumber(), sha256, certificate.getEncoded());
                    });
                } else {
                    validationResult.error("repository.object.is.not.a.trust.anchor.certificate", trustAnchorCertificateURI.toASCIIString());
                }

                if (rpkiObject != null) {
                    if (trustAnchor.getCertificate() == null || trustAnchor.getCertificate().getSerialNumber().compareTo(rpkiObject.getSerialNumber()) <= 0) {
                        trustAnchor.setCertificate(rpkiObject);
                    } else {
                        validationResult.warn("repository.object.is.older.than.previous.object", trustAnchorCertificateURI.toASCIIString());
                    }
                }
            }

            if (validationResult.hasFailures()) {
                validationRun.failed(validationResult.toString());
            } else {
                validationRun.succeeded();
            }
        } catch (CommandExecutionException | IOException e) {
            log.error("validation run for trust anchor {} failed", trustAnchor, e);
            validationRun.failed(e.toString());
        }

    }

    private File fetchTrustAnchorCertificate(TrustAnchor trustAnchor, URI trustAnchorCertificateURI) throws IOException {
        File trustAnchorDirectory = new File(localRsyncStorageDirectory, String.valueOf(trustAnchor.getId()));
        String trustAnchorHost = trustAnchorCertificateURI.getHost() + "/" + (trustAnchorCertificateURI.getPort() < 0 ? DEFAULT_RSYNC_PORT : trustAnchorCertificateURI.getPort());
        File targetFile = new File(
            new File(trustAnchorDirectory.getCanonicalFile(), trustAnchorHost),
            trustAnchorCertificateURI.getRawPath()
        ).getCanonicalFile();

        if (targetFile.getParentFile().mkdirs()) {
            log.info("created local rsync storage directory {} for trust anchor {}", targetFile.getParentFile(), trustAnchorCertificateURI);
        }

        Rsync rsync = new Rsync(trustAnchorCertificateURI.toASCIIString(), targetFile.getPath());
        rsync.addOptions("--update", "--times", "--copy-links");
        rsync.execute();

        log.info("Downloaded certificate {} to {}", trustAnchorCertificateURI, targetFile);
        return targetFile;
    }
}
