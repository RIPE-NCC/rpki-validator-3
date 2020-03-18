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
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.validator3.util.Http;
import org.jooq.lambda.tuple.Tuple2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.EOFException;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Keep metrics for an URL and a HTTP status code <i>or</i> a string describing the error (e.g. "handshake_failure").
 */
@Slf4j
@Service
public class HttpClientMetricsService {
    public final static int HISTOGRAM_HOURS = 6;

    @Autowired
    private MeterRegistry registry;

    private ConcurrentHashMap<Tuple2<String, String>, HttpStatusMetric> httpMetrics = new ConcurrentHashMap<>();

    public void update(String uri, String statusDescription, long durationMs) {
        final String relativeURL = URI.create(uri).resolve("/").toString();
        httpMetrics
            .computeIfAbsent(new Tuple2<>(relativeURL, statusDescription), key -> new HttpStatusMetric(registry, relativeURL, statusDescription))
            .update(durationMs);
    }

    /**
     * Unwrap an exception to get a more meaningful status to aggregate under in the metrics.
     * @param cause Throwable to unwrap
     * @return string description
     */
    public static String unwrapExceptionString(Throwable cause) {
        // HttpStatusException is a sub-type of Http.Failure: check it first.
        if (cause instanceof Http.HttpStatusException) {
            return String.valueOf(((Http.HttpStatusException)cause).getCode());
        } else if (cause instanceof Http.Failure) {
            final Throwable rootCause = cause.getCause();
            if (rootCause != null) {
                if (rootCause instanceof EOFException) {
                    // message is unique for each exception, group them by name
                    return rootCause.getClass().getName();
                }
                return rootCause.toString();
            }
        }
        return cause.getClass().getName();
    }

    public static class HttpStatusMetric {
        public final Counter responseStatusCounter;
        public final Timer responseTiming;

        public HttpStatusMetric(final MeterRegistry registry, final String uri, final String statusDescription) {
            this.responseStatusCounter = Counter.builder("http.response.status")
                    .description("HTTP request result (per server, per status)")
                    .tag("url", uri)
                    .tag("status", statusDescription)
                    .register(registry);
            this.responseTiming = Timer.builder("http.response.timing")
                    .description(String.format("HTTP response time (quantiles over requests in the last %d hours)", HISTOGRAM_HOURS))
                    .tag("url", uri)
                    .publishPercentiles(0.5, 0.95, 0.99)
                    .distributionStatisticExpiry(Duration.ofHours(HISTOGRAM_HOURS))
                    .register(registry);
        }

        public void update(long durationMs) {
            responseStatusCounter.increment();
            responseTiming.record(durationMs, TimeUnit.MILLISECONDS);
        }
    }
}
