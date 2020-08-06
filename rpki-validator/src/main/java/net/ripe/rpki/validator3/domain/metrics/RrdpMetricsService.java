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

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.jooq.lambda.tuple.Tuple2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RRDP Metrics service.
 */
@Slf4j
@Service
public class RrdpMetricsService {
    @Autowired
    private MeterRegistry registry;

    private ConcurrentHashMap<Tuple2<String, RRDPProcessingStatus>, RrdpMetric> rrdpMetrics = new ConcurrentHashMap<>();

    public void update(String uri, RRDPProcessingStatus status) {
        update(URI.create(uri), status);
    }

    public void update(URI uri, RRDPProcessingStatus status) {
        final String rootURL = uri.resolve("/").toASCIIString();
        rrdpMetrics
            .computeIfAbsent(new Tuple2<>(rootURL, status), key -> new RrdpMetric(registry, rootURL, status))
            .update();
    }

    private static class RrdpMetric {
        public final Counter responseStatusCounter;

        public RrdpMetric(final MeterRegistry registry, final String uri, final RRDPProcessingStatus status) {
            this.responseStatusCounter = Counter.builder("rpkivalidator.rrdp.status")
                    .description("Status of RRDP operation")
                    .tag("url", uri)
                    .tag("status", status.getDisplayString())
                    .register(registry);
        }

        public void update() {
            responseStatusCounter.increment();
        }
    }

    public enum RRDPProcessingStatus {

        ERROR_RETRIEVING("error_retrieving"),
        INVALID("invalid_response"),
        SNAPSHOT_FALLBACK_DELTA_FAILS("fallback_to_snapshot_delta_fails"),
        SNAPSHOT_FALLBACK_NEW_SESSION("fallback_to_snapshot_new_session"),
        SNAPSHOT_FALLBACK_LOCAL_AHEAD("fallback_to_snapshot_local_ahead"),
        SUCCESSFUL("success");
        
        private String displayString;
        RRDPProcessingStatus(String displayString) {
            this.displayString = displayString;
        }

        public String getDisplayString() {
            return displayString;
        }
    }
}