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
package net.ripe.rpki.validator3.api.roas;

import io.swagger.annotations.ApiModelProperty;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.validator3.api.Api;
import net.ripe.rpki.validator3.api.ApiResponse;
import net.ripe.rpki.validator3.api.trustanchors.TrustAnchorResource;
import net.ripe.rpki.validator3.domain.RpkiObjects;
import net.ripe.rpki.validator3.domain.Settings;
import net.ripe.rpki.validator3.domain.TrustAnchor;
import net.ripe.rpki.validator3.domain.TrustAnchors;
import net.ripe.rpki.validator3.domain.ValidatedRpkiObjects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.Links;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RestController
@RequestMapping(path = "/api/objects", produces = { Api.API_MIME_TYPE, "application/json" })
@Slf4j
public class ObjectController {
    @Autowired
    private RpkiObjects rpkiObjects;

    @Autowired
    private ValidatedRpkiObjects validatedRpkiObjects;

    @Autowired
    private TrustAnchors trustAnchors;

    @Autowired
    private Settings settings;

    @GetMapping(path = "/validated")
    public ResponseEntity<ApiResponse<ValidatedObjects>> list(Locale locale) {
        final Map<Long, TrustAnchorResource> trustAnchorsById = trustAnchors.findAll().stream()
            .collect(Collectors.toMap(
                TrustAnchor::getId,
                ta -> TrustAnchorResource.of(ta, locale))
            );
        final Map<Long, Links> trustAnchorLinks = trustAnchorsById.entrySet().stream()
            .collect(Collectors.toMap(
                entry -> entry.getKey(),
                entry -> new Links(entry.getValue().getLinks().getLink("self").withRel(TrustAnchor.TYPE)))
            );

        final Stream<RoaPrefix> validatedPrefixes = validatedRpkiObjects
            .findCurrentlyValidatedRoaPrefixes(null, null, null)
            .getObjects()
            .map(prefix -> {
                    Links links = trustAnchorLinks.get(prefix.getTrustAnchor().getId());
                    return new RoaPrefix(
                        String.valueOf(prefix.getAsn()),
                        prefix.getPrefix().toString(),
                        prefix.getEffectiveLength(),
                        links
                    );
                }
            )
            .distinct();

        final Stream<RouterCertificate> routerCertificates = validatedRpkiObjects.findCurrentlyValidatedRouterCertificates().getObjects()
            .map(o -> new RouterCertificate(o.getAsn(), o.getSubjectKeyIdentifier(), o.getSubjectPublicKeyInfo()));

        return ResponseEntity.ok(ApiResponse.<ValidatedObjects>builder()
                .data(new ValidatedObjects(settings.isInitialValidationRunCompleted(), trustAnchorsById.values(), validatedPrefixes, routerCertificates))
                .build());
    }

    @Value
    public static class ValidatedObjects {
        @ApiModelProperty(position = 1)
        boolean ready;
        @ApiModelProperty(position = 2)
        Collection<TrustAnchorResource> trustAnchors;
        @ApiModelProperty(position = 3)
        Stream<RoaPrefix> roas;
        @ApiModelProperty(position = 4)
        Stream<RouterCertificate> routerCertificates;
    }

    @Value
    public static class RoaPrefix {
        private String asn;
        private String prefix;
        private int maxLength;
        private Links links;
    }

    @Value
    public static class RouterCertificate {
        private List<String> asn;
        private String subjectKeyIdentifier;
        private String subjectPublicKeyInfo;
    }
}
