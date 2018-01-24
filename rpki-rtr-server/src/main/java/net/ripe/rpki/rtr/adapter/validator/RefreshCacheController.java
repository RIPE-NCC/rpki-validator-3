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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.List;

import static java.util.stream.Collectors.toList;

@Service
@Slf4j
public class RefreshCacheController {
    private final RestTemplate restTemplate;

    @Value("${rpki.validator.validated.roas.uri}")
    private URI validatedRoasUri;

    @Autowired
    private RtrCache cache;

    @Autowired
    private RtrClients clients;

    public RefreshCacheController(RestTemplateBuilder restTemplateBuilder) {
        log.info("RefreshCacheController loaded");
        this.restTemplate = restTemplateBuilder.build();
    }

    @Scheduled(initialDelay = 10_000L, fixedDelay = 60_000L)
    private void refreshCache() {
        log.info("fetching validated roa prefixes from {}", validatedRoasUri);
        ValidatedRoasResponse response = restTemplate.getForObject(validatedRoasUri, ValidatedRoasResponse.class);

        ValidatedRoas validatedRoas = response.getData();
        if (!validatedRoas.ready) {
            log.warn("validator {} not ready yet, will retry later", validatedRoasUri);
            return;
        }

        List<ValidatedPrefix> validatedPrefixes = validatedRoas.getRoas();
        log.info("fetched {} validated roa prefixes from {}", validatedPrefixes.size(), validatedRoasUri);

        List<RtrDataUnit> roaPrefixes = validatedPrefixes.stream().map(prefix -> RtrDataUnit.prefix(
            Asn.parse(prefix.getAsn()),
            IpRange.parse(prefix.getPrefix()),
            prefix.getMaxLength()
        )).distinct().collect(toList());

        cache.update(roaPrefixes).ifPresent(updatedSerialNumber -> {
            clients.cacheUpdated(cache.getSessionId(), updatedSerialNumber);
        });
    }

    @lombok.Value
    public static class ValidatedRoasResponse {
        public ValidatedRoas data;
    }

    @lombok.Value
    public static class ValidatedRoas {
        boolean ready;
        List<ValidatedPrefix> roas;
    }

    @lombok.Value
    static class ValidatedPrefix {
        String asn;
        Integer maxLength;
        String prefix;
    }
}
