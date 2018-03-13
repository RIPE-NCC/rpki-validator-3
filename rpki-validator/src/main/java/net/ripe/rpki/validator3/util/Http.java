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

import lombok.extern.slf4j.Slf4j;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.api.Response;
import org.eclipse.jetty.client.util.InputStreamResponseListener;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.function.Supplier;

@Slf4j
public class Http {

    public static class NotModified extends RuntimeException {
    }

    public static class Failure extends RuntimeException {
        public Failure(String s) {
            super(s);
        }

        public Failure(String s, Exception e) {
            super(s, e);
        }
    }

    public static <T> T readStream(final Supplier<Request> requestF, Function<InputStream, T> reader) {
        InputStreamResponseListener listener = new InputStreamResponseListener();
        Request request = requestF.get();
        request.send(listener);

        Response response = null;
        try {
            response = listener.get(30, TimeUnit.SECONDS);

            log.debug("response.getStatus() = " + response.getStatus());

            if (response.getStatus() != 200) {
                if (response.getStatus() == 304) {
                    final NotModified error = new NotModified();
                    response.abort(error);
                    throw error;
                } else {
                    final Failure error = new Failure("unexpected response status " + response.getStatus() + " for " + request.getURI());
                    response.abort(error);
                    throw error;
                }
            }

            try (InputStream inputStream = listener.getInputStream()) {
                return reader.apply(inputStream);
            }
        } catch (IOException | InterruptedException | TimeoutException | ExecutionException e) {
            final Failure error = new Failure("failed reading response stream for " + request.getURI() + ": " + e, e);
            if (response != null) {
                response.abort(error);
            }
            log.error("Error ", e);
            throw error;
        }
    }
}
