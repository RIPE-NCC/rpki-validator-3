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

import com.google.common.util.concurrent.AtomicDouble;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.jooq.lambda.tuple.Tuple2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Keep metrics for an URL and a HTTP status code <i>or</i> a string describing the error (e.g. "handshake_failure").
 */
@Slf4j
@Service
public class RsyncMetricsService {
    @Autowired
    private MeterRegistry registry;

    private ConcurrentHashMap<Tuple2<String, Integer>, RsyncMetric> rsyncMetrics = new ConcurrentHashMap<>();

    public void update(String uri, int statusDescription, long durationMs) {
        update(URI.create(uri), statusDescription, durationMs);
    }

    public void update(URI uri, int statusDescription, long durationMs) {
        final String relativeURL = uri.resolve("/").toASCIIString();
        rsyncMetrics
            .computeIfAbsent(new Tuple2<>(relativeURL, statusDescription), key -> new RsyncMetric(registry, relativeURL, statusDescription))
            .update(durationMs);
    }

    private static class RsyncMetric {
        public final Counter responseStatusCounter;
        public final Timer responseDuration;

        public RsyncMetric(final MeterRegistry registry, final String uri, final long exitCode) {
            this.responseStatusCounter = Counter.builder("rpkivalidator.rsync.status")
                    .description("Exit code of the rsync command")
                    .tag("url", uri)
                    .tag("status", String.valueOf(exitCode))
                    .register(registry);

            this.responseDuration = Timer.builder("rpkivalidator.rsync.duration")
                    .description("Duration of rsync in seconds")
                    .tag("url", uri)
                    .register(registry);
        }

        public void update(long durationMs) {
            responseStatusCounter.increment();
            responseDuration.record(durationMs, TimeUnit.MILLISECONDS);
        }
    }
}
