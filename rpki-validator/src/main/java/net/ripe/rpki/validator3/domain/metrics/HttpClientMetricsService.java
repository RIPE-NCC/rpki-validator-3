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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Keep metrics for an URL and a HTTP status code <i>or</i> a string describing the error (e.g. "handshake_failure").
 */
@Slf4j
@Service
public class HttpClientMetricsService {
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
        // Unwrap the failure and return message
        if (cause instanceof Http.Failure) {
            final Throwable rootCause = cause.getCause();
            if (rootCause instanceof EOFException) {
                return rootCause.getClass().getName();
            }
            return rootCause.toString();
        } else if (cause instanceof Http.HttpStatusException) {
            return String.valueOf(((Http.HttpStatusException)cause).getCode());
        }
        return "exception";
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
                    .tag("url", uri)
                    .publishPercentiles(0.5, 0.95, 0.99)
                    .register(registry);
        }

        public void update(long durationMs) {
            responseStatusCounter.increment();
            responseTiming.record(durationMs, TimeUnit.MILLISECONDS);
        }
    }
}
