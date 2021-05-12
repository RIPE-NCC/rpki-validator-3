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
package net.ripe.rpki.validator3.rrdp;

import com.google.common.io.ByteStreams;
import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import com.google.common.hash.HashingInputStream;
import com.google.common.hash.HashingOutputStream;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.validator3.api.util.BuildInformation;
import net.ripe.rpki.validator3.domain.metrics.HttpClientMetricsService;
import net.ripe.rpki.validator3.util.HttpStreaming;
import net.ripe.rpki.validator3.util.Time;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.Request;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Function;

import static org.springframework.util.StreamUtils.copy;

@Component
@Slf4j
public class HttpRrdpClient implements RrdpClient {
    private final HttpClientMetricsService httpMetrics;

    private HttpClient httpClient;

    private final BuildInformation buildInformation;

    @Autowired
    public HttpRrdpClient(HttpClient httpClient, HttpClientMetricsService httpMetrics, BuildInformation buildInformation) {
        this.httpClient = httpClient;
        this.buildInformation = buildInformation;
        this.httpMetrics = httpMetrics;
    }

    @Override
    public <T> T readStream(final String uri, Function<InputStream, T> reader) {
        long before = System.currentTimeMillis();
        String statusDescription = "200";
        try {
            return HttpStreaming.readStream(() -> {
                final Request request = httpClient.newRequest(uri);
                final String version = buildInformation.getVersion();
                return request;
            }, reader);
        } catch (Exception e) {
            statusDescription = HttpClientMetricsService.unwrapExceptionString(e);
            throw new RrdpException("Error downloading '" + uri + "', cause: " + fullMessage(e), e);
        } finally {
            httpMetrics.update(uri, statusDescription, System.currentTimeMillis() - before);
        }
    }

    private static String fullMessage(Throwable t) {
        final StringBuilder s = new StringBuilder();
        while (true) {
            s.append(t.getMessage());
            if (t == t.getCause()) {
                return s.toString();
            }
            t = t.getCause();
            if (t == null) {
                return s.toString();
            }
            s.append(", cause: ");
        }
    }

    @Override
    public byte[] getBody(String uri) {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        readStream(uri, s -> {
            try {
                return copy(s, baos);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        return baos.toByteArray();
    }

    @Override
    public <T> T processUsingTemporaryFile(String uri, HashFunction hashFunction, BiFunction<Path, HashCode, T> process) {
        try {
            // Creates a file with default permissions (only readable/writable by owner)
            final Path tempFile = Files.createTempFile("rrdp-", ".tmp");
            try {
                return readStream(uri, in -> {
                    HashingInputStream hashingStream = new HashingInputStream(hashFunction, in);

                    Long timedDownload = Time.timed(() -> {
                        try (OutputStream out = new FileOutputStream(tempFile.toFile())) {
                            ByteStreams.copy(hashingStream, out);
                        } catch (IOException e) {
                            throw new RrdpException("Error downloading '" + uri + "', cause: " + fullMessage(e), e);
                        }
                    });
                    log.info("file {} of {} bytes downloaded in {}ms", uri, tempFile.toFile().length(), timedDownload);

                    return process.apply(tempFile, hashingStream.hash());
                });
            } finally {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ignored) {
                }
            }
        } catch (IOException e) {
            throw new RrdpException("Error downloading '" + uri + "', cause: " + fullMessage(e), e);
        }
    }
}
