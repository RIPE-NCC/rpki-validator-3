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
package net.ripe.rpki.validator3.api.bgp;

import com.google.common.collect.ImmutableList;
import lombok.extern.slf4j.Slf4j;
import net.ripe.ipresource.Asn;
import net.ripe.ipresource.IpRange;
import net.ripe.rpki.validator3.domain.metrics.HttpClientMetricsService;
import net.ripe.rpki.validator3.util.HttpStreaming;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.Request;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.validation.constraints.NotNull;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

@Component
@Slf4j
public class BgpRisDownloader {
    private final HttpClientMetricsService httpMetrics;

    private final HttpClient httpClient;

    @Autowired
    public BgpRisDownloader(HttpClientMetricsService httpMetrics, HttpClient httpClient) {
        this.httpMetrics = httpMetrics;
        this.httpClient = httpClient;

        assert httpClient.isStarted();
    }

    public <T> BgpRisDump<T> fetch(@NotNull BgpRisDump dump, Function<BgpRisEntry, Stream<T>> mapper) {
        log.info("attempting to download new BGP RIS preview dump from {}", dump.url);
        long before = System.currentTimeMillis();
        String statusDescription = "200";

        final Supplier<Request> requestSupplier = () -> {
            final Request request = httpClient.newRequest(dump.url);
            if (dump.lastModified != null) {
                final String lastModifiedFormatted = formatAsRFC2616(dump.lastModified);
                log.debug("Adding 'If-Modified-Since' equals to {}", lastModifiedFormatted);
                request.header("If-Modified-Since", lastModifiedFormatted);
            }
            return request;
        };
        final BiFunction<InputStream, Long, BgpRisDump> streamReader = (stream, lastModified) -> {
            try {
                Stream<BgpRisEntry> entries = parse(new GZIPInputStream(stream));
                // Collect the stream to a list here to avoid closing the HTTP stream before
                // all entries have been parsed.
                ImmutableList.Builder<T> builder = ImmutableList.builder();
                entries.flatMap(mapper).forEach(builder::add);
                return BgpRisDump.of(dump.url, new DateTime(lastModified), Optional.of(builder.build()));
            } catch (Exception e) {
                log.error("Error downloading RIS dump: " + dump.url);
                return dump;
            }
        };

        try {
            return  HttpStreaming.readStream(requestSupplier, streamReader);
        } catch (HttpStreaming.NotModifiedException n) {
            statusDescription = "302";
            return dump;
        } catch (Exception e) {
            statusDescription = HttpClientMetricsService.unwrapExceptionString(e);
            throw e;
        } finally {
            httpMetrics.update(dump.url, statusDescription, System.currentTimeMillis() - before);
        }
    }

    public static Stream<BgpRisEntry> parse(final InputStream is) {
        return new BufferedReader(new InputStreamReader(is)).lines()
                .map(s -> {
                    try {
                        return parseLine(s);
                    } catch (Exception e) {
                        log.error("Unparseable line: " + s);
                        return null;
                    }
                })
                .filter(Objects::nonNull);
    }

    private static Pattern regexp = Pattern.compile("^\\s*([0-9]+)\\s+([0-9a-fA-F.:/]+)\\s+([0-9]+)\\s*$");

    private static BgpRisEntry parseLine(final String line) {
        final Matcher matcher = regexp.matcher(line);
        if (matcher.matches()) {
            final Asn asn = Asn.parse(matcher.group(1));
            final IpRange prefix = IpRange.parse(matcher.group(2));
            final int visibility = Integer.parseInt(matcher.group(3));
            return BgpRisEntry.of(asn, prefix, visibility);
        }
        return null;
    }

    private String formatAsRFC2616(DateTime d) {
        return d.toDateTime(DateTimeZone.UTC).toString("EEE, dd MMM yyyy HH:mm:ss ZZZ");
    }
}
