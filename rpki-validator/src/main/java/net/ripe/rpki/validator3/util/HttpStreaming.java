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
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.api.Response;
import org.eclipse.jetty.client.util.InputStreamResponseListener;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

@Slf4j
public class HttpStreaming {
    public static <T> T readStream(final Supplier<Request> requestF, Function<InputStream,  T> reader) {
        BiFunction<InputStream, Long, T> ignoreLastModified = (stream, ignoredLastModified) -> reader.apply(stream);
        return readStream(requestF, ignoreLastModified);
    }

    public static <T> T readStream(final Supplier<Request> requestF, BiFunction<InputStream, Long,  T> reader) {
        InputStreamResponseListener listener = new InputStreamResponseListener();

        Request request = requestF.get();
        request.send(listener);

        Response response = null;
        try {
            response = listener.get(30, TimeUnit.SECONDS);

            if (response.getStatus() != 200) {
                if (response.getStatus() == 304) {
                    final NotModifiedException error = new NotModifiedException(request.getURI().toString());
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
            final HttpFailureException error = new HttpFailureException("failed reading response stream for " + request.getURI() + ": " + e, e);
            if (response != null) {
                response.abort(error);
            }
            throw error;
        } catch (ExecutionException e) {
            final HttpFailureException error = new HttpFailureException("failed reading response stream for " + request.getURI() + ": " + e.getCause(), e.getCause());
            if (response != null) {
                response.abort(error);
            }
            throw error;
        }
    }

    public static class HttpFailureException extends RuntimeException {
        public HttpFailureException(String s) {
            super(s);
        }

        public HttpFailureException(String s, Throwable cause) {
            super(s, cause);
        }
    }

    @Getter
    public static class HttpStatusException extends HttpFailureException {
        private int code;

        public HttpStatusException(Response response, Request request) {
            this(response.getStatus(), String.format("unexpected response status %d for %s", response.getStatus(), request.getURI()));
        }

        public HttpStatusException(int code, String message) {
            super(message);
            this.code = code;
        }
    }

    public static class NotModifiedException extends HttpStatusException {
        public NotModifiedException(String uri) {
            super(302, String.format("HTTP 302 NOT MODIFIED for %s", uri));
        }
    }
}
