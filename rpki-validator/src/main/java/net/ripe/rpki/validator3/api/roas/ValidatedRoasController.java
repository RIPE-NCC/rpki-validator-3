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
import io.swagger.annotations.ApiParam;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.validator3.api.ValidatorApi;
import net.ripe.rpki.validator3.api.ApiResponse;
import net.ripe.rpki.validator3.api.Metadata;
import net.ripe.rpki.validator3.api.Paging;
import net.ripe.rpki.validator3.api.SearchTerm;
import net.ripe.rpki.validator3.api.Sorting;
import net.ripe.rpki.validator3.domain.ValidatedRoaPrefix;
import net.ripe.rpki.validator3.domain.validation.ValidatedRpkiObjects;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.Links;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.stream.Stream;

import static net.ripe.rpki.validator3.api.ModelPropertyDescriptions.*;
import static org.springframework.hateoas.mvc.ControllerLinkBuilder.methodOn;

@RestController
@RequestMapping(path = "/api/roas", produces = {ValidatorApi.API_MIME_TYPE, "application/json"})
@Slf4j
public class ValidatedRoasController {
    @Autowired
    private ValidatedRpkiObjects validatedRpkiObjects;

    @GetMapping
    public ResponseEntity<ApiResponse<Stream<RoaPrefix>>> list(
            @RequestParam(name = "startFrom", defaultValue = "0") long startFrom,
            @RequestParam(name = "pageSize", defaultValue = "20") long pageSize,
            @RequestParam(name = "search", defaultValue = "", required = false) String searchString,
            @ApiParam(allowableValues = SORT_BY_ALLOWABLE_VALUES)
            @RequestParam(name = "sortBy", defaultValue = "prefix") String sortBy,
            @ApiParam(allowableValues = SORT_DIRECTION_ALLOWABLE_VALUES)
            @RequestParam(name = "sortDirection", defaultValue = "asc") String sortDirection) {

        final SearchTerm searchTerm = StringUtils.isNotBlank(searchString) ? new SearchTerm(searchString) : null;
        final Sorting sorting = Sorting.parse(sortBy, sortDirection);
        final Paging paging = Paging.of(startFrom, pageSize);

        ValidatedRpkiObjects.ValidatedObjects<ValidatedRoaPrefix> currentlyValidatedRoaPrefixes = validatedRpkiObjects.findCurrentlyValidatedRoaPrefixes(searchTerm, sorting, paging);
        final Stream<RoaPrefix> roas = currentlyValidatedRoaPrefixes.getObjects()
            .map(prefix -> new RoaPrefix(
                String.valueOf(prefix.getAsn()),
                prefix.getPrefix().toString(),
                prefix.getEffectiveLength(),
                prefix.getTrustAnchor().getName(),
                prefix.getLocations().first()
            ));

        long totalSize = currentlyValidatedRoaPrefixes.getTotalCount();
        final Links links = Paging.links(
                startFrom, pageSize, totalSize,
                (sf, ps) -> methodOn(ValidatedRoasController.class).list(sf, ps, searchString, sortBy, sortDirection));
        return ResponseEntity.ok(
                ApiResponse.<Stream<RoaPrefix>>builder()
                        .links(links)
                        .metadata(Metadata.of(totalSize))
                        .data(roas).build()
        );
    }

    @Value
    class RoaPrefix {
        @ApiModelProperty(value = ASN_PROPERTY, example = ASN_EXAMPLE)
        private String asn;
        private String prefix;
        @ApiModelProperty(MAXLENGTH_PROPERTY)
        private int length;
        @ApiModelProperty(value = TRUST_ANCHOR, example = TRUST_ANCHOR_EXAMPLE)
        private String trustAnchor;
        private String uri;
    }
}
