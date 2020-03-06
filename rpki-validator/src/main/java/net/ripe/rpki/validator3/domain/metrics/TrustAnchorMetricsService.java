package net.ripe.rpki.validator3.domain.metrics;


import io.micrometer.core.instrument.*;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.validator3.storage.Storage;
import net.ripe.rpki.validator3.storage.data.Key;
import net.ripe.rpki.validator3.storage.data.TrustAnchor;
import net.ripe.rpki.validator3.storage.data.validation.ValidationCheck;
import net.ripe.rpki.validator3.storage.data.validation.CertificateTreeValidationRun;
import net.ripe.rpki.validator3.storage.stores.ValidationRuns;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
@Setter
public class TrustAnchorMetricsService {
    @Autowired
    private MeterRegistry registry;

    @Autowired
    private Storage storage;

    @Autowired
    private ValidationRuns validationRuns;

    /**
     * Creating multiple counters for the same trust anchor has no negative side-effects.
     */
    private ConcurrentHashMap<String, CertificateTreeValidationMetrics> cetrificateTreeValidationMetrics = new ConcurrentHashMap<>();

    public void update(TrustAnchor ta, CertificateTreeValidationRun vr, long durationMs) {
        final String uri = ta.getLocations().size() > 0 ? ta.getLocations().get(0) : null;
        if (uri == null) {
            log.error("Trust anchor {} does not have a location.", ta.getSubjectPublicKeyInfo());
            return;
        }

        cetrificateTreeValidationMetrics
                .computeIfAbsent(uri, key -> new CertificateTreeValidationMetrics(ta))
                .update(vr, durationMs);
    }

    private class CertificateTreeValidationMetrics {
        private final String rsyncPrefetchUri;

        private final AtomicInteger warningCount;
        private final AtomicInteger errorCount;
        private final AtomicInteger objectCount;

        private final AtomicLong lastSuccessfulValidationRunTime;
        private Key lastCertificateTreeValidationRunKey = Key.of(-1);

        private final Counter validationRunFailedCount;
        private final Counter validationRunSuccessCount;

        private final Timer validationRunDuration;

        public CertificateTreeValidationMetrics(TrustAnchor trustAnchor) {
            this.objectCount = new AtomicInteger(0);
            this.errorCount = new AtomicInteger(0);
            this.warningCount = new AtomicInteger(0);

            this.lastSuccessfulValidationRunTime = new AtomicLong(0);

            this.rsyncPrefetchUri = trustAnchor.getLocations().get(0);

            this.validationRunDuration = Timer.builder("validation_run_duration")
                    .description("Duration for the validation of the certificates descendant from this trust anchor.")
                    .tag("trust_anchor", rsyncPrefetchUri)
                    .register(registry);

            this.validationRunSuccessCount = Counter.builder("validation_run_count")
                    .description("Number of validation runs for the tree of certificates for this trust anchor.")
                    .tag("trust_anchor", rsyncPrefetchUri)
                    .tag("succeeded", "true")
                    .register(registry);
            this.validationRunFailedCount = Counter.builder("validation_run_count")
                    .tag("trust_anchor", rsyncPrefetchUri)
                    .tag("succeeded", "false")
                    .register(registry);

            Gauge.builder("validation_results", objectCount::get)
                    .description("Status of the objects under this trust anchor (identical to numbers on front page of web interface)")
                    .tag("status", "total")
                    .tag("trust_anchor", rsyncPrefetchUri)
                    .register(registry);

            Gauge.builder("validation_results", errorCount::get)
                    .description("Status of the objects under this trust anchor (identical to numbers on front page of web interface)")
                    .tag("status", "error")
                    .tag("trust_anchor", rsyncPrefetchUri)
                    .register(registry);

            Gauge.builder("validation_results", warningCount::get)
                    .description("Status of the objects under this trust anchor (identical to numbers on front page of web interface)")
                    .tag("status", "warning")
                    .tag("trust_anchor", rsyncPrefetchUri)
                    .register(registry);

            Gauge.builder("last_validation_run", lastSuccessfulValidationRunTime::get)
                    .description("Timestamp (in seconds) of the last successful validation run.")
                    .tag("trust_anchor", rsyncPrefetchUri)
                    .register(registry);
        }

        public void update(CertificateTreeValidationRun vr, long durationMs) {
            final Key currentRunKey = vr.key();
            if (!lastCertificateTreeValidationRunKey.equals(currentRunKey)) {
                lastCertificateTreeValidationRunKey = currentRunKey;

                if (vr.isSucceeded()) {
                    validationRunSuccessCount.increment();
                    lastSuccessfulValidationRunTime.set(vr.getCompletedAt().toEpochMilli()/1000);
                } else {
                    validationRunFailedCount.increment();
                }

                validationRunDuration.record(durationMs, TimeUnit.MILLISECONDS);
                errorCount.set(vr.countChecks(ValidationCheck.Status.ERROR));

                storage.readTx0(tx -> {
                    objectCount.set(validationRuns.getObjectCount(tx, vr));
                });
                warningCount.set(vr.countChecks(ValidationCheck.Status.WARNING));
            }
        }
    }
}
