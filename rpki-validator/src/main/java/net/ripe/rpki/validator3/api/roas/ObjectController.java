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

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.annotations.ApiOperation;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.validator3.api.ValidatorApi;
import net.ripe.rpki.validator3.api.ApiResponse;
import net.ripe.rpki.validator3.api.bgpsec.BgpSecAssertionsService;
import net.ripe.rpki.validator3.api.bgpsec.BgpSecFilterService;
import net.ripe.rpki.validator3.api.ignorefilters.IgnoreFilterService;
import net.ripe.rpki.validator3.api.roaprefixassertions.RoaPrefixAssertionsService;
import net.ripe.rpki.validator3.api.trustanchors.TrustAnchorResource;
import net.ripe.rpki.validator3.domain.IgnoreFiltersPredicate;
import net.ripe.rpki.validator3.domain.validation.ValidatedRpkiObjects;
import net.ripe.rpki.validator3.storage.Storage;
import net.ripe.rpki.validator3.storage.data.RpkiRepository;
import net.ripe.rpki.validator3.storage.data.TrustAnchor;
import net.ripe.rpki.validator3.storage.stores.RpkiRepositories;
import net.ripe.rpki.validator3.storage.stores.TrustAnchors;
import org.springframework.beans.factory.annotation.Autowired;
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

import static net.ripe.rpki.validator3.api.ModelPropertyDescriptions.*;

@RestController
@Api(tags = "Validated objects")
@RequestMapping(path = "/api/objects", produces = { ValidatorApi.API_MIME_TYPE, "application/json" })
@Slf4j
public class ObjectController {

    @Autowired
    private ValidatedRpkiObjects validatedRpkiObjects;

    @Autowired
    private TrustAnchors trustAnchors;

    @Autowired
    private RpkiRepositories rpkiRepositories;

    @Autowired
    private IgnoreFilterService ignoreFilters;

    @Autowired
    private RoaPrefixAssertionsService roaPrefixAssertions;

    @Autowired
    private BgpSecAssertionsService bgpSecAssertions;

    @Autowired
    private BgpSecFilterService bgpSecFilterService;

    @Autowired
    private Storage storage;

    @ApiOperation("get all validated objects (used by rpki-rtr-server)")
    @GetMapping(path = "/validated")
    public ResponseEntity<ApiResponse<ValidatedObjects>> list(Locale locale) {
        final List<TrustAnchor> trustAnchorList = storage.readTx(tx -> trustAnchors.findAll(tx));

        final Stream<RoaPrefix> validatedPrefixes = validatedRpkiObjects
            .findCurrentlyValidatedRoaPrefixes(null, null, null)
            .getObjects()
            .filter(new IgnoreFiltersPredicate(ignoreFilters.all()).negate())
            .map(prefix -> new RoaPrefix(
                String.valueOf(prefix.getAsn()),
                prefix.getPrefix().toString(),
                prefix.getEffectiveLength())
            );

        final Stream<RoaPrefix> assertions = roaPrefixAssertions
            .all()
            .map(assertion -> new RoaPrefix(
                assertion.getAsn().toString(),
                assertion.getPrefix().toString(),
                assertion.getMaxPrefixLength() != null ? assertion.getMaxPrefixLength() : assertion.getPrefix().getPrefixLength()
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

        final boolean noPendingRepositories = storage.readTx(tx ->
            trustAnchorList.stream().allMatch(ta -> {
                final Map<RpkiRepository.Status, Long> statusLongMap = rpkiRepositories.countByStatus(tx, ta.key(), true);
                final Long pendingRepoNumber = statusLongMap.get(RpkiRepository.Status.PENDING);
                return pendingRepoNumber == null || pendingRepoNumber == 0L;
            }));

        final boolean allTasDoneInitialLoading = storage.readTx(tx -> trustAnchors.allInitialCertificateTreeValidationRunsCompleted(tx));

        final List<TrustAnchorResource> trustAnchorResources = trustAnchorList.stream()
            .map(ta -> TrustAnchorResource.of(ta, Locale.ROOT))
            .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.<ValidatedObjects>builder()
            .data(new ValidatedObjects(
                allTasDoneInitialLoading && noPendingRepositories,
                trustAnchorResources,
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
        @ApiModelProperty(value = ASN_PROPERTY, example = ASN_EXAMPLE)
        private String asn;
        @ApiModelProperty(PREFIX_EXAMPLE)
        private String prefix;
        private int maxLength;
    }

    @Value
    public static class RouterCertificate {
        @ApiModelProperty(ASN_LIST_PROPERTY)
        private List<String> asn;
        private String subjectKeyIdentifier;
        private String subjectPublicKeyInfo;
    }
}
