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
import java.util.*;

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

        try {
            X509ResourceCertificate certificate = trustAnchor.getCertificate();
            validationResult.warnIfNull(certificate, "trust.anchor.certificate.available");
            if (certificate == null) {
                return;
            }

            CertificateRepositoryObjectValidationContext context = new CertificateRepositoryObjectValidationContext(
                URI.create(trustAnchorLocation),
                certificate
            );

            certificate.validate(trustAnchorLocation, context, null, null, VALIDATION_OPTIONS, validationResult);
            if (validationResult.hasFailureForCurrentLocation()) {
                return;
            }

            URI rrdpNotifyUri = certificate.getRrdpNotifyUri();
            validationResult.warnIfNull(rrdpNotifyUri, "trust.anchor.certificate.rrdp.notify.uri.present");
            if (rrdpNotifyUri == null) {
                return;
            }

            validationRun.getValidatedObjects().addAll(
                validateCertificateAuthority(trustAnchor, context, validationResult)
            );
        } finally {
            addValidationResults(validationRun, validationResult);
            if (validationResult.hasFailures()) {
                log.info("tree validation failed for {}", trustAnchor);
                validationRun.setFailed();
            } else {
                log.info("tree validation succeeded for {}", trustAnchor);
                validationRun.setSucceeded();
            }
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

    private List<RpkiObject> validateCertificateAuthority(TrustAnchor trustAnchor, CertificateRepositoryObjectValidationContext context, ValidationResult validationResult) {
        List<RpkiObject> validatedObjects = new ArrayList<>();

        ValidationLocation certificateLocation = validationResult.getCurrentLocation();
        ValidationResult temporary = ValidationResult.withLocation(certificateLocation);
        try {
            RpkiRepository rpkiRepository = rpkiRepositories.register(trustAnchor, context.getRpkiNotifyURI().toASCIIString());

            temporary.warnIfTrue(rpkiRepository.isPending(), "rpki.repository.pending", context.getRpkiNotifyURI().toASCIIString());
            temporary.rejectIfTrue(rpkiRepository.isFailed(), "rpki.repository.failed", context.getRpkiNotifyURI().toASCIIString());
            if (rpkiRepository.isPending() || temporary.hasFailureForCurrentLocation()) {
                return validatedObjects;
            }

            X509ResourceCertificate certificate = context.getCertificate();
            URI manifestUri = certificate.getManifestUri();
            temporary.setLocation(new ValidationLocation(manifestUri));

            Optional<RpkiObject> manifestObject = rpkiObjects.findLatestByTypeAndAuthorityKeyIdentifier(RpkiObject.Type.MFT, context.getSubjectKeyIdentifier());
            temporary.rejectIfFalse(manifestObject.isPresent(), "rpki.manifest.found", manifestUri.toASCIIString());
            Optional<ManifestCms> maybeManifest = manifestObject.flatMap(x -> x.get(ManifestCms.class, temporary));
            if (temporary.hasFailureForCurrentLocation()) {
                return validatedObjects;
            }

            ManifestCms manifest = maybeManifest.get();
            URI crlUri = manifest.getCrlUri();
            temporary.rejectIfNull(crlUri, "manifest.crl.uri");
            if (temporary.hasFailureForCurrentLocation()) {
                return validatedObjects;
            }

            temporary.setLocation(new ValidationLocation(crlUri));
            Optional<RpkiObject> crlObject = rpkiObjects.findLatestByTypeAndAuthorityKeyIdentifier(RpkiObject.Type.CRL, context.getSubjectKeyIdentifier());
            temporary.rejectIfFalse(crlObject.isPresent(), "rpki.crl.found", crlUri.toASCIIString());
            Optional<X509Crl> crl = crlObject.flatMap(x -> x.get(X509Crl.class, temporary));
            if (temporary.hasFailureForCurrentLocation()) {
                return validatedObjects;
            }

            crl.get().validate(crlUri.toASCIIString(), context, null, VALIDATION_OPTIONS, temporary);
            if (temporary.hasFailureForCurrentLocation()) {
                return validatedObjects;
            }

            temporary.setLocation(new ValidationLocation(manifestUri));
            manifest.validate(manifestUri.toASCIIString(), context, crl.get(), manifest.getCrlUri(), VALIDATION_OPTIONS, temporary);
            if (temporary.hasFailureForCurrentLocation()) {
                return validatedObjects;
            }

            Map<URI, RpkiObject> manifestEntries = retrieveManifestEntries(manifest, manifestUri, temporary);

            manifestEntries.forEach((location, obj) -> {
                temporary.setLocation(new ValidationLocation(location));
                temporary.rejectIfFalse(Arrays.equals(Sha256.hash(obj.getEncoded()), obj.getSha256()), "rpki.object.sha256.matches");
                if (temporary.hasFailureForCurrentLocation()) {
                    return;
                }

                Optional<CertificateRepositoryObject> maybeCertificateRepositoryObject = obj.get(CertificateRepositoryObject.class, temporary);
                if (temporary.hasFailureForCurrentLocation()) {
                    return;
                }

                maybeCertificateRepositoryObject.ifPresent(certificateRepositoryObject -> {
                    certificateRepositoryObject.validate(location.toASCIIString(), context, crl.get(), crlUri, VALIDATION_OPTIONS, temporary);

                    if (!temporary.hasFailureForCurrentLocation()) {
                        validatedObjects.add(obj);
                    }

                    if (certificateRepositoryObject instanceof X509ResourceCertificate
                        && ((X509ResourceCertificate) certificateRepositoryObject).isCa()
                        && !temporary.hasFailureForCurrentLocation()) {

                        CertificateRepositoryObjectValidationContext childContext = context.createChildContext(location, (X509ResourceCertificate) certificateRepositoryObject);
                        validatedObjects.addAll(validateCertificateAuthority(trustAnchor, childContext, temporary));
                    }
                });
            });
        } finally {
            validationResult.addAll(temporary);
        }

        return validatedObjects;
    }

    private Map<URI, RpkiObject> retrieveManifestEntries(ManifestCms manifest, URI manifestUri, ValidationResult validationResult) {
        Map<URI, RpkiObject> result = new LinkedHashMap<>();
        for (Map.Entry<String, byte[]> entry : manifest.getFiles().entrySet()) {
            URI location = manifestUri.resolve(entry.getKey());
            validationResult.setLocation(new ValidationLocation(location));

            Optional<RpkiObject> object = rpkiObjects.findBySha256(entry.getValue());
            validationResult.rejectIfFalse(object.isPresent(), "manifest.entry.found", manifestUri.toASCIIString());

            object.ifPresent(obj -> {
                boolean hashMatches = Arrays.equals(obj.getSha256(), entry.getValue());
                validationResult.rejectIfFalse(hashMatches, "manifest.entry.hash.matches", entry.getKey());
                if (!hashMatches) {
                    return;
                }

                result.put(location, obj);
            });
        }
        return result;
    }
}
