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
package net.ripe.rpki.rtr.adapter.validator;

import lombok.extern.slf4j.Slf4j;
import net.ripe.ipresource.Asn;
import net.ripe.ipresource.IpRange;
import net.ripe.rpki.rtr.domain.RtrCache;
import net.ripe.rpki.rtr.domain.RtrClients;
import net.ripe.rpki.rtr.domain.RtrDataUnit;
import net.ripe.rpki.rtr.domain.RtrRouterKey;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.Base64;
import java.util.List;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;

@Service
@Slf4j
public class RefreshCacheController {
    private final RestTemplate restTemplate;

    @Value("${rpki.validator.validated.objects.uri}")
    private URI validatedObjectsUri;

    @Autowired
    private RtrCache cache;

    @Autowired
    private RtrClients clients;

    public RefreshCacheController(RestTemplateBuilder restTemplateBuilder) {
        log.info("RefreshCacheController loaded");
        this.restTemplate = restTemplateBuilder.build();
    }

    public void refreshObjectCache() {
        log.info("fetching validated roa prefixes from {}", validatedObjectsUri);
        ValidatedObjectsResponse response = restTemplate.getForObject(validatedObjectsUri, ValidatedObjectsResponse.class);

        ValidatedObjects validatedObjects = response.getData();
        if (!validatedObjects.ready) {
            log.warn("validator {} not ready yet, will retry later", validatedObjectsUri);
            return;
        }

        List<ValidatedPrefix> validatedPrefixes = validatedObjects.getRoas();
        log.info("fetched {} validated roa prefixes from {}", validatedPrefixes.size(), validatedObjectsUri);

        final Stream<? extends RtrDataUnit> roaPrefixes = validatedPrefixes.stream().map(prefix -> RtrDataUnit.prefix(
                Asn.parse(prefix.getAsn()),
                IpRange.parse(prefix.getPrefix()),
                prefix.getMaxLength()
        )).distinct();

        final Base64.Decoder decoder = Base64.getDecoder();
        final Stream<? extends RtrDataUnit> routerCertificates = validatedObjects.getRouterCertificates().stream().flatMap(rc ->
                rc.asn.stream().map(a -> {
                    final int asn = Math.toIntExact(Asn.parse(a).longValue());
                    return RtrRouterKey.of(decoder.decode(rc.subjectKeyIdentifier), decoder.decode(rc.subjectPublicKeyInfo), asn);
                }));

        final List<RtrDataUnit> cacheEntries = Stream.concat(roaPrefixes, routerCertificates).collect(toList());
        cache.update(cacheEntries).ifPresent(updatedSerialNumber -> {
            clients.cacheUpdated(cache.getSessionId(), updatedSerialNumber);
        });
    }

    @lombok.Value
    public static class ValidatedObjectsResponse {
        public ValidatedObjects data;
    }

    @lombok.Value
    public static class ValidatedObjects {
        boolean ready;
        List<ValidatedPrefix> roas;
        List<RouterCertificate> routerCertificates;
    }

    @lombok.Value
    static class ValidatedPrefix {
        String asn;
        Integer maxLength;
        String prefix;
    }

    @lombok.Value
    public static class RouterCertificate {
        private List<String> asn;
        private String subjectKeyIdentifier;
        private String subjectPublicKeyInfo;
    }
}
