package net.ripe.rpki.validator3.domain.cleanup;

import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.validator3.domain.ValidationRuns;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import java.time.Duration;
import java.time.Instant;

@Service
@Slf4j
public class ValidationRunCleanupService {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private ValidationRuns validationRuns;

    private final Duration cleanupGraceDuration;

    public ValidationRunCleanupService(@Value("${rpki.validator.validation.run.cleanup.grace.duration}") String cleanupGraceDuration) {
        this.cleanupGraceDuration = Duration.parse(cleanupGraceDuration);
    }

    @Scheduled(initialDelay = 60_000, fixedDelayString = "${rpki.validator.validation.run.cleanup.interval.ms}")
    @Transactional
    public long cleanupValidationRuns() {

        // Delete all validation runs older than `cleanupGraceDuration` that have a later validation run.
        Instant completedBefore = Instant.now().minus(cleanupGraceDuration);
        long removedCount = validationRuns.removeOldValidationRuns(completedBefore);
        log.info("Removed {} old validation runs", removedCount);
        return removedCount;
    }
}
