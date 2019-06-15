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
package net.ripe.rpki.validator3.api.ignorefilters;

import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Produces;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.validator3.api.Api;
import net.ripe.rpki.validator3.api.ApiCommand;
import net.ripe.rpki.validator3.api.ApiResponse;
import net.ripe.rpki.validator3.api.Metadata;
import net.ripe.rpki.validator3.api.Paging;
import net.ripe.rpki.validator3.api.SearchTerm;
import net.ripe.rpki.validator3.api.Sorting;
import net.ripe.rpki.validator3.api.roas.ObjectController;
import net.ripe.rpki.validator3.domain.IgnoreFiltersPredicate;
import net.ripe.rpki.validator3.domain.validation.ValidatedRpkiObjects;
import org.apache.commons.lang.StringUtils;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.Links;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import io.micronaut.http.annotation.QueryValue;

import javax.inject.Inject;
import javax.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.springframework.hateoas.mvc.ControllerLinkBuilder.linkTo;
import static org.springframework.hateoas.mvc.ControllerLinkBuilder.methodOn;

@Slf4j
@Controller( "/api/ignore-filters")
@Produces( { Api.API_MIME_TYPE, "application/json" })
public class IgnoreFiltersController {

    @Inject
    private IgnoreFilterService ignoreFilterService;

    @Inject
    private ValidatedRpkiObjects validatedRpkiObjects;

    @Get
    public ResponseEntity<ApiResponse<Stream<IgnoreFilterDto>>> list(
            @QueryValue(value = "startFrom", defaultValue = "0") long startFrom,
            @QueryValue(value = "pageSize", defaultValue = "20") long pageSize,
            //TODO: required false?
            @QueryValue(value = "search", defaultValue = "") String searchString,
            @QueryValue(value = "sortBy", defaultValue = "prefix") String sortBy,
            @QueryValue(value = "sortDirection", defaultValue = "asc") String sortDirection) {

        final SearchTerm searchTerm = StringUtils.isNotBlank(searchString) ? new SearchTerm(searchString) : null;
        final Sorting sorting = Sorting.parse(sortBy, sortDirection);
        final Paging paging = Paging.of(startFrom, pageSize);

        final Stream<IgnoreFilter> matching = ignoreFilterService.find(searchTerm, sorting, paging);
        int totalSize = (int) ignoreFilterService.count(searchTerm);

        final Links links = Paging.links(
                startFrom, pageSize, totalSize,
                (sf, ps) -> methodOn(IgnoreFiltersController.class).list(sf, ps, searchString, sortBy, sortDirection));

        return ResponseEntity.ok(
                ApiResponse.<Stream<IgnoreFilterDto>>builder()
                        .links(links)
                        .metadata(Metadata.of(totalSize))
                        .data(matching.map(this::toIgnoreFilter))
                        .build()
        );
    }

    @Get("/{id}")
    public ResponseEntity<ApiResponse<IgnoreFilterDto>> get(@PathVariable long id) {
        return ResponseEntity.ok(ignoreFilterResource(ignoreFilterService.get(id)));
    }

    @PostMapping(consumes = { Api.API_MIME_TYPE, "application/json" })
    public ResponseEntity<ApiResponse<IgnoreFilterDto>> add(@RequestBody @Valid ApiCommand<AddIgnoreFilter> command) throws Exception {
        final long id = ignoreFilterService.execute(command.getData());
        final IgnoreFilter ignoreFilter = ignoreFilterService.get(id);
        final Link selfRel = linkTo(methodOn(IgnoreFiltersController.class).get(id)).withSelfRel();
        return ResponseEntity.created(URI.create(selfRel.getHref())).body(ignoreFilterResource(ignoreFilter));
    }

    @DeleteMapping(path = "/{id}")
    public ResponseEntity<?> delete(@PathVariable long id) {
        ignoreFilterService.remove(id);
        return ResponseEntity.noContent().build();
    }

    private IgnoreFilterDto toIgnoreFilter(IgnoreFilter f) {
        final IgnoreFiltersPredicate ignoreFiltersPredicate = new IgnoreFiltersPredicate(Stream.of(f));
        final List<ObjectController.RoaPrefix> affectedRoas = validatedRpkiObjects
                .findCurrentlyValidatedRoaPrefixes(null, null, null)
                .getObjects()
                .filter(ignoreFiltersPredicate)
                .map(prefix -> new ObjectController.RoaPrefix(
                        String.valueOf(prefix.getAsn()),
                        prefix.getPrefix().toString(),
                        prefix.getEffectiveLength(),
                        null)
                ).collect(Collectors.toList());
        return new IgnoreFilterDto(f, affectedRoas);
    }

    private ApiResponse<IgnoreFilterDto> ignoreFilterResource(IgnoreFilter ignoreFilter) {
        return ApiResponse.<IgnoreFilterDto>builder().data(new IgnoreFilterDto(ignoreFilter)).build();
    }
}
