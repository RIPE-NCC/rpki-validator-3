package net.ripe.rpki.validator3.domain.validation;

import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.commons.crypto.x509cert.X509ResourceCertificate;
import net.ripe.rpki.commons.validation.ValidationLocation;
import net.ripe.rpki.commons.validation.ValidationResult;
import net.ripe.rpki.commons.validation.ValidationStatus;
import net.ripe.rpki.commons.validation.objectvalidators.CertificateRepositoryObjectValidationContext;
import net.ripe.rpki.validator3.domain.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.net.URI;
import java.util.Optional;

@Service
@Slf4j
public class CertificateTreeValidationService {
    private final TrustAnchors trustAnchors;
    private final RpkiObjects rpkiObjects;
    private final RpkiRepositories rpkiRepositories;
    private final ValidationRuns validationRuns;

    @Autowired
    public CertificateTreeValidationService(TrustAnchors trustAnchors, RpkiObjects rpkiObjects, RpkiRepositories rpkiRepositories, ValidationRuns validationRuns) {
        this.trustAnchors = trustAnchors;
        this.rpkiObjects = rpkiObjects;
        this.rpkiRepositories = rpkiRepositories;
        this.validationRuns = validationRuns;
    }

    @Transactional(Transactional.TxType.REQUIRED)
    public void validate(long trustAnchorId) {
        TrustAnchor trustAnchor = trustAnchors.get(trustAnchorId);
        log.info("starting tree validation for {}", trustAnchor);


        CertificateTreeValidationRun validationRun = new CertificateTreeValidationRun(trustAnchor);
        validationRuns.add(validationRun);

        ValidationResult validationResult = ValidationResult.withLocation(trustAnchor.getLocations().get(0));

        X509ResourceCertificate certificate = trustAnchor.getCertificate();
        if (certificate == null) {
            validationResult.warn("trust.anchor.certificate.available");
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
        validationResult.setLocation(new ValidationLocation(rrdpNotifyUri));

        CertificateRepositoryObjectValidationContext context = new CertificateRepositoryObjectValidationContext(
            URI.create(trustAnchor.getLocations().get(0)),
            certificate
        );

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
        RpkiRepository rpkiRepository = rpkiRepositories.register(trustAnchor, context.getRpkiNotifyURI().toASCIIString());

        if (rpkiRepository.isPending() || rpkiRepository.isFailed()) {
            validationResult.warnIfTrue(rpkiRepository.isPending(), "rpki.repository.pending");
            validationResult.rejectIfTrue(rpkiRepository.isFailed(), "rpki.repository.failed");
            return;
        }

        Optional<RpkiObject> manifest = rpkiObjects.findLatestByTypeAndAuthorityKeyIdentifier(RpkiObject.Type.MFT, context.getSubjectKeyIdentifier());
        Optional<RpkiObject> crl = rpkiObjects.findLatestByTypeAndAuthorityKeyIdentifier(RpkiObject.Type.CRL, context.getSubjectKeyIdentifier());

//        context.getCertificate().validate();
//        context.validate(context.get);
//        validationResult.setLocation(Vali)
        validationResult.rejectIfFalse(manifest.isPresent(), "rpki.manifest.found", context.getCertificate().getManifestUri().toASCIIString());
        // Validate CRL and manifest
    }
}
