package net.ripe.rpki.validator3.domain.validation;

import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.commons.crypto.CertificateRepositoryObject;
import net.ripe.rpki.commons.crypto.cms.manifest.ManifestCms;
import net.ripe.rpki.commons.crypto.crl.X509Crl;
import net.ripe.rpki.commons.crypto.x509cert.X509ResourceCertificate;
import net.ripe.rpki.commons.validation.ValidationLocation;
import net.ripe.rpki.commons.validation.ValidationOptions;
import net.ripe.rpki.commons.validation.ValidationResult;
import net.ripe.rpki.commons.validation.ValidationStatus;
import net.ripe.rpki.commons.validation.objectvalidators.CertificateRepositoryObjectValidationContext;
import net.ripe.rpki.validator3.domain.*;
import net.ripe.rpki.validator3.util.Sha256;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.persistence.EntityManager;
import javax.persistence.FlushModeType;
import javax.transaction.Transactional;
import java.net.URI;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
public class CertificateTreeValidationService {
    private static final ValidationOptions VALIDATION_OPTIONS = new ValidationOptions();
    private final EntityManager entityManager;
    private final TrustAnchors trustAnchors;
    private final RpkiObjects rpkiObjects;
    private final RpkiRepositories rpkiRepositories;
    private final ValidationRuns validationRuns;

    @Autowired
    public CertificateTreeValidationService(
        EntityManager entityManager,
        TrustAnchors trustAnchors,
        RpkiObjects rpkiObjects,
        RpkiRepositories rpkiRepositories,
        ValidationRuns validationRuns
    ) {
        this.entityManager = entityManager;
        this.trustAnchors = trustAnchors;
        this.rpkiObjects = rpkiObjects;
        this.rpkiRepositories = rpkiRepositories;
        this.validationRuns = validationRuns;
    }

    @Transactional(Transactional.TxType.REQUIRED)
    public void validate(long trustAnchorId) {
        entityManager.setFlushMode(FlushModeType.COMMIT);

        TrustAnchor trustAnchor = trustAnchors.get(trustAnchorId);
        log.info("starting tree validation for {}", trustAnchor);


        CertificateTreeValidationRun validationRun = new CertificateTreeValidationRun(trustAnchor);
        validationRuns.add(validationRun);

        String trustAnchorLocation = trustAnchor.getLocations().get(0);
        ValidationResult validationResult = ValidationResult.withLocation(trustAnchorLocation);

        X509ResourceCertificate certificate = trustAnchor.getCertificate();
        if (certificate == null) {
            validationResult.warn("trust.anchor.certificate.available");
            addValidationResults(validationRun, validationResult);
            validationRun.failed();
            return;
        }

        CertificateRepositoryObjectValidationContext context = new CertificateRepositoryObjectValidationContext(
            URI.create(trustAnchorLocation),
            certificate
        );

        certificate.validate(trustAnchorLocation, context, null, null, VALIDATION_OPTIONS, validationResult);
        if (validationResult.hasFailureForCurrentLocation()) {
            addValidationResults(validationRun, validationResult);
            validationRun.failed();
            return;
        }

        URI rrdpNotifyUri = certificate.getRrdpNotifyUri();
        if (rrdpNotifyUri == null) {
            validationResult.warn("trust.anchor.certificate.rrdp.notify.uri.present");
            addValidationResults(validationRun, validationResult);
            validationRun.failed();
            return;
        }

        validateCertificateAuthority(trustAnchor, context, validationResult);

        addValidationResults(validationRun, validationResult);

        if (validationResult.hasFailures()) {
            validationRun.failed();
        } else {
            validationRun.succeeded();
        }
    }

    private void addValidationResults(ValidationRun validationRun, ValidationResult validationResult) {
        for (ValidationLocation location : validationResult.getValidatedLocations()) {
            for (net.ripe.rpki.commons.validation.ValidationCheck check : validationResult.getAllValidationChecksForLocation(location)) {
                if (check.getStatus() != ValidationStatus.PASSED) {
                    ValidationCheck validationCheck = new ValidationCheck(validationRun, null, location.getName(), check);
                    validationRun.addCheck(validationCheck);
                }
            }
        }

    }

    private void validateCertificateAuthority(TrustAnchor trustAnchor, CertificateRepositoryObjectValidationContext context, ValidationResult validationResult) {
        ValidationLocation certificateLocation = validationResult.getCurrentLocation();

        RpkiRepository rpkiRepository = rpkiRepositories.register(trustAnchor, context.getRpkiNotifyURI().toASCIIString());

        validationResult.warnIfTrue(rpkiRepository.isPending(), "rpki.repository.pending", context.getRpkiNotifyURI().toASCIIString());
        validationResult.rejectIfTrue(rpkiRepository.isFailed(), "rpki.repository.failed", context.getRpkiNotifyURI().toASCIIString());
        if (validationResult.hasFailureForCurrentLocation()) {
            return;
        }

        X509ResourceCertificate certificate = context.getCertificate();

        Optional<RpkiObject> manifestObject = rpkiObjects.findLatestByTypeAndAuthorityKeyIdentifier(RpkiObject.Type.MFT, context.getSubjectKeyIdentifier());
        validationResult.rejectIfFalse(manifestObject.isPresent(), "rpki.manifest.found", certificate.getManifestUri().toASCIIString());
        if (validationResult.hasFailureForCurrentLocation()) {
            return;
        }

        validationResult.setLocation(new ValidationLocation(certificate.getManifestUri()));
        Optional<ManifestCms> manifest = manifestObject.flatMap(x -> x.get(ManifestCms.class, validationResult));
        if (validationResult.hasFailureForCurrentLocation()) {
            return;
        }

        validationResult.rejectIfNull(manifest.get().getCrlUri(), "manifest.crl.uri");
        if (validationResult.hasFailureForCurrentLocation()) {
            return;
        }

        validationResult.setLocation(certificateLocation);
        URI crlURI = manifest.get().getCrlUri();
        Optional<RpkiObject> crlObject = rpkiObjects
            .findLatestByTypeAndAuthorityKeyIdentifier(RpkiObject.Type.CRL, context.getSubjectKeyIdentifier());
        validationResult.rejectIfFalse(crlObject.isPresent(), "rpki.crl.found", crlURI.toASCIIString());
        if (validationResult.hasFailureForCurrentLocation()) {
            return;
        }

        validationResult.setLocation(new ValidationLocation(crlURI));
        Optional<X509Crl> crl = crlObject.flatMap(x -> x.get(X509Crl.class, validationResult));
        if (validationResult.hasFailureForCurrentLocation()) {
            return;
        }

        crl.ifPresent(x -> {
            x.validate(crlURI.toASCIIString(), context, null, VALIDATION_OPTIONS, validationResult);
        });
        if (validationResult.hasFailureForCurrentLocation()) {
            return;
        }

        manifest.ifPresent(x -> {
            x.validate(certificate.getManifestUri().toASCIIString(), context, crl.get(), crlURI, VALIDATION_OPTIONS, validationResult);
        });
        if (validationResult.hasFailureForCurrentLocation()) {
            return;
        }

        for (Map.Entry<String, byte[]> entry : manifest.get().getFiles().entrySet()) {
            Optional<RpkiObject> object = rpkiObjects.findBySha256(entry.getValue());
            validationResult.setLocation(new ValidationLocation(certificate.getManifestUri()));
            validationResult.rejectIfFalse(object.isPresent(), "manifest.entry.found", entry.getKey());
            object.ifPresent(obj -> {
                URI location = URI.create(certificateLocation.getName()).resolve(entry.getKey());
                validationResult.setLocation(new ValidationLocation(location));
                validationResult.rejectIfFalse(Arrays.equals(Sha256.hash(obj.getEncoded()), obj.getSha256()), "rpki.object.sha256.matches");

                Optional<CertificateRepositoryObject> certificateRepositoryObject = obj.get(CertificateRepositoryObject.class, validationResult);
                certificateRepositoryObject.ifPresent(x -> {
                    if (x instanceof X509ResourceCertificate) {
                        X509ResourceCertificate childCertificate = (X509ResourceCertificate) x;
                        CertificateRepositoryObjectValidationContext childContext = context.createChildContext(location, childCertificate);
                        validateCertificateAuthority(trustAnchor, childContext, validationResult);
                    } else {
                        x.validate(location.toASCIIString(), context, crl.get(), crlURI, VALIDATION_OPTIONS, validationResult);
                    }
                });
            });
        }
    }
}
