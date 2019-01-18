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

import lombok.extern.slf4j.Slf4j;
import net.ripe.ipresource.Asn;
import net.ripe.ipresource.IpRange;
import net.ripe.ipresource.UniqueIpResource;
import net.ripe.rpki.validator3.util.Http;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.Request;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.validation.constraints.NotNull;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

@Component
@Slf4j
public class BgpRisDownloader {

    private final Http http;

    private HttpClient httpClient;

    @Autowired
    public BgpRisDownloader(Http http) {
        this.http = http;
    }

    @PostConstruct
    public void postConstruct() throws Exception {
        httpClient = http.client();
        httpClient.start();
    }

    @PreDestroy
    public void preDestory() throws Exception {
        httpClient.stop();
    }

    public BgpRisDump fetch(@NotNull BgpRisDump dump) {
        log.info("attempting to download new BGP RIS preview dump from {}", dump.url);
        final Supplier<Request> requestSupplier = () -> {
            final Request request = httpClient.newRequest(dump.url);
            if (dump.lastModified != null) {
                log.debug("Adding 'If-Modified-Since' equals to {}", formatAsRFC2616(dump.lastModified));
                request.header("If-Modified-Since", formatAsRFC2616(dump.lastModified));
            }
            return request;
        };
        final BiFunction<InputStream, Long, BgpRisDump> streamReader = (stream, lastModified) -> {
            try {
                List<BgpRisEntry> entries = parse(new GZIPInputStream(stream));
                return BgpRisDump.of(dump.url, new DateTime(lastModified), Optional.of(entries));
            } catch (Exception e) {
                log.error("Error downloading RIS dump: " + dump.url);
                return dump;
            }
        };

        try {
            return  Http.readStream(requestSupplier, streamReader);
        } catch (Http.NotModified n) {
            return dump;
        }
    }

    public static List<BgpRisEntry> parse(final InputStream is) {
        final BufferedReader reader = new BufferedReader(new InputStreamReader(is));

        final IdentityMap id = new IdentityMap();
        return reader.lines().map(s -> {
            try {
                return parseLine(s, id::unique);
            } catch (Exception e) {
                log.error("Unparseable line: " + s);
                return null;
            }
        }).filter(Objects::nonNull).collect(Collectors.toList());
    }

    private static Pattern regexp = Pattern.compile("^\\s*([0-9]+)\\s+([0-9a-fA-F.:/]+)\\s+([0-9]+)\\s*$");

    private static BgpRisEntry parseLine(final String line, final Function<Object, Object> uniq) {
        final Matcher matcher = regexp.matcher(line);
        if (matcher.matches()) {
            final Asn asn = (Asn) uniq.apply(Asn.parse(matcher.group(1)));
            IpRange parsed = IpRange.parse(matcher.group(2));
            final UniqueIpResource start = (UniqueIpResource) uniq.apply(parsed.getStart());
            final UniqueIpResource end = (UniqueIpResource) uniq.apply(parsed.getEnd());
            final IpRange prefix = (IpRange) start.upTo(end);
            final int visibility = Integer.parseInt(matcher.group(3));
            return BgpRisEntry.of(asn, prefix, visibility);
        }
        return null;
    }

    // This is to avoid distinct object instances for objects that are equal
    private static class IdentityMap {
        private Map<Object, Object> unique = new HashMap<>();

        Object unique(final Object o) {
            final Object u = unique.get(o);
            if (u == null) {
                unique.put(o, o);
                return o;
            }
            return u;
        }
    }

    private String formatAsRFC2616(DateTime d) {
        return d.toDateTime(DateTimeZone.UTC).toString("EEE, dd MMM yyyy HH:mm:ss ZZZ");
    }
}
