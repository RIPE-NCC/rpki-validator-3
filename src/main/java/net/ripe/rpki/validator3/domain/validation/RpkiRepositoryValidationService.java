package net.ripe.rpki.validator3.domain.validation;

import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.commons.validation.ValidationResult;
import net.ripe.rpki.validator3.domain.*;
import net.ripe.rpki.validator3.rrdp.RrdpService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.persistence.EntityManager;
import javax.persistence.FlushModeType;
import javax.transaction.Transactional;
import java.util.Locale;

@Service
@Slf4j
public class RpkiRepositoryValidationService {

    private final EntityManager entityManager;
    private final ValidationRuns validationRunRepository;
    private final RpkiRepositories rpkiRepositories;
    private final RrdpService rrdpService;

    @Autowired
    public RpkiRepositoryValidationService(
        EntityManager entityManager,
        ValidationRuns validationRunRepository,
        RpkiRepositories rpkiRepositories,
        RrdpService rrdpService
    ) {
        this.entityManager = entityManager;
        this.validationRunRepository = validationRunRepository;
        this.rpkiRepositories = rpkiRepositories;
        this.rrdpService = rrdpService;
    }

    @Transactional(Transactional.TxType.REQUIRED)
    public void validateRpkiRepository(long rpkiRepositoryId) {
        entityManager.setFlushMode(FlushModeType.COMMIT);

        final RpkiRepository rpkiRepository = rpkiRepositories.get(rpkiRepositoryId);
        log.info("Starting RPKI repository validation for " + rpkiRepository);

        ValidationResult validationResult = ValidationResult.withLocation(rpkiRepository.getRrdpNotifyUri());

        final RpkiRepositoryValidationRun validationRun = new RpkiRepositoryValidationRun(rpkiRepository);
        validationRunRepository.add(validationRun);

        final String uri = rpkiRepository.getRrdpNotifyUri();
        if (isRrdpUri(uri)) {
            rrdpService.storeRepository(rpkiRepository, validationRun);
            rpkiRepository.setDownloaded();
        } else if (isRsyncUri(uri)) {
            validationResult.error("rsync.repository.not.supported");
        } else {
            log.error("Unsupported type of the URI " + uri);
        }

        if (validationResult.hasFailures()) {
            validationRun.setFailed();
        } else {
            validationRun.setSucceeded();
        }

        if (validationRun.isSucceeded() && validationRun.getAddedObjectCount() > 0) {
            rpkiRepository.getTrustAnchors().forEach(trustAnchor -> {
                validationRunRepository.runCertificateTreeValidation(trustAnchor);
            });
        }
    }

    private boolean isRrdpUri(final String uri) {
        return uri.toLowerCase(Locale.ROOT).startsWith("https://") || uri.toLowerCase(Locale.ROOT).startsWith("http://");
    }

    private boolean isRsyncUri(final String uri) {
        return uri.toLowerCase(Locale.ROOT).startsWith("rsync://");
    }
}
