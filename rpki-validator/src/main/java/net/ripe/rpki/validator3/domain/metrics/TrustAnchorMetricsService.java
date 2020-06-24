/**
 * The BSD License
 *
 * Copyright (c) 2010-2018 RIPE NCC
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *   - Redistributions of source code must retain the above copyright notice,
 *     this list of conditions and the following disclaimer.
 *   - Redistributions in binary form must reproduce the above copyright notice,
 *     this list of conditions and the following disclaimer in the documentation
 *     and/or other materials provided with the distribution.
 *   - Neither the name of the RIPE NCC nor the names of its contributors may be
 *     used to endorse or promote products derived from this software without
 *     specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
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

    private ConcurrentHashMap<String, CertificateTreeValidationMetrics> certificateTreeValidationMetrics = new ConcurrentHashMap<>();

    public void update(TrustAnchor ta, CertificateTreeValidationRun vr, long durationMs) {
        final String uri = ta.getLocations().size() > 0 ? ta.getLocations().get(0) : null;
        if (uri == null) {
            log.error("Trust anchor {} does not have a location.", ta.getSubjectPublicKeyInfo());
            return;
        }

        certificateTreeValidationMetrics
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

            this.validationRunDuration = Timer.builder("rpkivalidator.validation.run.duration")
                    .description("Duration for the validation of the certificates descendant from this trust anchor.")
                    .tag("trust_anchor", rsyncPrefetchUri)
                    .register(registry);

            this.validationRunSuccessCount = Counter.builder("rpkivalidator.validation.run.total")
                    .description("Number of validation runs for the tree of certificates for this trust anchor.")
                    .tag("trust_anchor", rsyncPrefetchUri)
                    .tag("succeeded", "true")
                    .register(registry);
            this.validationRunFailedCount = Counter.builder("rpkivalidator.validation.run.total")
                    .tag("trust_anchor", rsyncPrefetchUri)
                    .tag("succeeded", "false")
                    .register(registry);

            Gauge.builder("rpkivalidator.validated.objects", objectCount::get)
                    .description("Status of the objects under this trust anchor (identical to numbers on front page of web interface)")
                    .tag("status", "total")
                    .tag("trust_anchor", rsyncPrefetchUri)
                    .register(registry);

            Gauge.builder("rpkivalidator.validated.objects", errorCount::get)
                    .description("Status of the objects under this trust anchor (identical to numbers on front page of web interface)")
                    .tag("status", "error")
                    .tag("trust_anchor", rsyncPrefetchUri)
                    .register(registry);

            Gauge.builder("rpkivalidator.validated.objects", warningCount::get)
                    .description("Status of the objects under this trust anchor (identical to numbers on front page of web interface)")
                    .tag("status", "warning")
                    .tag("trust_anchor", rsyncPrefetchUri)
                    .register(registry);

            Gauge.builder("rpkivalidator.last.validation.run", lastSuccessfulValidationRunTime::get)
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
