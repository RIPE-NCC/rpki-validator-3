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
import net.ripe.ipresource.Asn;
import net.ripe.ipresource.IpRange;
import net.ripe.rpki.validator3.api.Api;
import net.ripe.rpki.validator3.api.ApiResponse;
import net.ripe.rpki.validator3.api.bgpsec.BgpSecFilterService;
import net.ripe.rpki.validator3.api.trustanchors.TrustAnchorResource;
import net.ripe.rpki.validator3.domain.BgpSecAssertions;
import net.ripe.rpki.validator3.domain.IgnoreFilters;
import net.ripe.rpki.validator3.domain.IgnoreFiltersPredicate;
import net.ripe.rpki.validator3.domain.RoaPrefixAssertions;
import net.ripe.rpki.validator3.domain.Settings;
import net.ripe.rpki.validator3.domain.validation.ValidatedRpkiObjects;
import net.ripe.rpki.validator3.storage.Lmdb;
import net.ripe.rpki.validator3.storage.data.TrustAnchor;
import net.ripe.rpki.validator3.storage.stores.RpkiObjectStore;
import net.ripe.rpki.validator3.storage.stores.SettingsStore;
import net.ripe.rpki.validator3.storage.stores.TrustAnchorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.Links;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;
import java.util.Collections;
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
    private ValidatedRpkiObjects validatedRpkiObjects;

    @Autowired
    private TrustAnchorStore trustAnchors;

    @Autowired
    private IgnoreFilters ignoreFilters;

    @Autowired
    private RoaPrefixAssertions roaPrefixAssertions;

    @Autowired
    private BgpSecAssertions bgpSecAssertions;

    @Autowired
    private BgpSecFilterService bgpSecFilterService;

    @Autowired
    private SettingsStore settings;

    @Autowired
    private Lmdb lmdb;

    @GetMapping(path = "/validated")
    public ResponseEntity<ApiResponse<ValidatedObjects>> list(Locale locale) {
        final Map<Long, TrustAnchorResource> trustAnchorsById = lmdb.readTx(tx ->
                trustAnchors.findAll(tx).stream()
                        .collect(Collectors.toMap(
                                ta -> ta.key().asLong(),
                                ta -> TrustAnchorResource.of(ta, locale))
                        ));
        final Map<Long, Links> trustAnchorLinks = trustAnchorsById.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> new Links(entry.getValue().getLinks().getLink("self").withRel(TrustAnchor.TYPE)))
                );

        final Stream<RoaPrefix> validatedPrefixes = validatedRpkiObjects
            .findCurrentlyValidatedRoaPrefixes(null, null, null)
            .getObjects()
            .filter(new IgnoreFiltersPredicate(ignoreFilters.all()).negate())
            .map(prefix -> {
                    Links links = trustAnchorLinks.get(prefix.getTrustAnchor().getId());
                    return new RoaPrefix(
                        String.valueOf(prefix.getAsn()),
                        prefix.getPrefix().toString(),
                        prefix.getEffectiveLength(),
                        links
                    );
                }
            );

        final Stream<RoaPrefix> assertions = roaPrefixAssertions
            .all()
            .map(assertion -> new RoaPrefix(
                new Asn(assertion.getAsn()).toString(),
                IpRange.parse(assertion.getPrefix()).toString(),
                assertion.getMaximumLength() != null ? assertion.getMaximumLength() : IpRange.parse(assertion.getPrefix()).getPrefixLength(),
                null
            ));

        final Stream<RoaPrefix> combinedPrefixes = Stream.concat(validatedPrefixes, assertions).distinct();

        final Stream<ValidatedRpkiObjects.RouterCertificate> certificates = validatedRpkiObjects.findCurrentlyValidatedRouterCertificates().getObjects();
        final Stream<RouterCertificate> filteredRouterCertificates = bgpSecFilterService.filterCertificates(certificates)
                .map(o -> new RouterCertificate(o.getAsn(), o.getSubjectKeyIdentifier(), o.getSubjectPublicKeyInfo()));

        final Stream<RouterCertificate> bgpSecAssertions = this.bgpSecAssertions.all().map(b -> {
            final List<String> asns = Collections.singletonList(String.valueOf(b.getAsn()));
            return new RouterCertificate(asns, b.getSki(), b.getPublicKey());
        });

        final Stream<RouterCertificate> combinedAssertions = Stream.concat(filteredRouterCertificates, bgpSecAssertions).distinct();

        return ResponseEntity.ok(ApiResponse.<ValidatedObjects>builder()
                .data(new ValidatedObjects(
                        lmdb.readTx(settings::isInitialValidationRunCompleted),
                        trustAnchorsById.values(),
                        combinedPrefixes,
                        combinedAssertions))
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
