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
package net.ripe.rpki.validator3.util;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.validator3.domain.metrics.HttpClientMetricsService;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.HttpProxy;
import org.eclipse.jetty.client.ProxyConfiguration;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.api.Response;
import org.eclipse.jetty.client.util.InputStreamResponseListener;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

@Component
@Slf4j
public class Http {
    @Autowired
    private HttpClientMetricsService httpMetrics;

    @Value("${rpki.validator.rrdp.trust.all.tls.certificates}")
    private boolean trustAllTlsCertificates;

    @Value("${rpki.validator.http.proxy.host:#{null}}")
    private String proxyHost;

    @Value("${rpki.validator.http.proxy.port:#{null}}")
    private Integer proxyPort;

    public HttpClient client() {
        final SslContextFactory sslContextFactory = new SslContextFactory();
        sslContextFactory.setTrustAll(trustAllTlsCertificates);
        HttpClient httpClient = new HttpClient(sslContextFactory);
        log.info("Trust all TLS certificates: {}, proxy host is {}, proxy port is {}", trustAllTlsCertificates, proxyHost, proxyPort);
        if (proxyHost != null && proxyPort != null) {
            ProxyConfiguration proxyConfig = httpClient.getProxyConfiguration();
            HttpProxy proxy = new HttpProxy(proxyHost, proxyPort);
            proxyConfig.getProxies().add(proxy);
        }
        httpClient.setSocketAddressResolver(new HappyEyeballsResolver(httpClient));
        return httpClient;
    }

    public static class NotModified extends HttpStatusException {
        public NotModified(String uri) {
            super(302, String.format("HTTP 302 NOT MODIFIED for %s", uri));
        }
    }

    @Getter
    public static class HttpStatusException extends Failure {
        private int code;

        public HttpStatusException(Response response, Request request) {
            this(response.getStatus(), String.format("unexpected response status %d for %s", response.getStatus(), request.getURI()));
        }

        public HttpStatusException(int code, String message) {
            super(message);
            this.code = code;
        }
    }

    public static class Failure extends RuntimeException {
        public Failure(String s) {
            super(s);
        }

        public Failure(String s, Throwable cause) {
            super(s, cause);
        }
    }

    public static <T> T readStream(final Supplier<Request> requestF, Function<InputStream,  T> reader) {
        BiFunction<InputStream, Long, T> ignoreLastModified = (stream, ignoredLastModified) -> reader.apply(stream);
        return readStream(requestF, ignoreLastModified);
    }

    public static <T> T readStream(final Supplier<Request> requestF, BiFunction<InputStream, Long,  T> reader) {
        InputStreamResponseListener listener = new InputStreamResponseListener();

        Request request = requestF.get();
        request.header("User-Agent", "RIPE NCC RPKI Validator version 3");
        request.send(listener);

        Response response = null;
        try {
            response = listener.get(30, TimeUnit.SECONDS);

            if (response.getStatus() != 200) {
                if (response.getStatus() == 304) {
                    final NotModified error = new NotModified(request.getURI().toString());
                    response.abort(error);
                    throw error;
                } else {
                    final HttpStatusException error = new HttpStatusException(response, request);
                    response.abort(error);
                    throw error;
                }
            }

            long lastModified = response.getHeaders().getDateField("Last-Modified");
            try (InputStream inputStream = listener.getInputStream()) {
                return reader.apply(inputStream, lastModified);
            }
        } catch (IOException | InterruptedException | TimeoutException e) {
            final Failure error = new Failure("failed reading response stream for " + request.getURI() + ": " + e, e);
            if (response != null) {
                response.abort(error);
            }
            throw error;
        } catch (ExecutionException e) {
            final Failure error = new Failure("failed reading response stream for " + request.getURI() + ": " + e.getCause(), e.getCause());
            if (response != null) {
                response.abort(error);
            }
            throw error;

        }
    }
}
