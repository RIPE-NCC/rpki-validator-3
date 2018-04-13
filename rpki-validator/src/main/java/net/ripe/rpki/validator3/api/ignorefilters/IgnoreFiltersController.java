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

import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.validator3.api.Api;
import net.ripe.rpki.validator3.api.ApiCommand;
import net.ripe.rpki.validator3.api.ApiResponse;
import net.ripe.rpki.validator3.api.Metadata;
import net.ripe.rpki.validator3.api.Paging;
import net.ripe.rpki.validator3.api.SearchTerm;
import net.ripe.rpki.validator3.api.Sorting;
import net.ripe.rpki.validator3.api.roas.ObjectController;
import net.ripe.rpki.validator3.domain.IgnoreFilters;
import net.ripe.rpki.validator3.domain.IgnoreFiltersPredicate;
import net.ripe.rpki.validator3.domain.ValidatedRpkiObjects;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.Links;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.springframework.hateoas.mvc.ControllerLinkBuilder.linkTo;
import static org.springframework.hateoas.mvc.ControllerLinkBuilder.methodOn;

@RestController
@Slf4j
@RequestMapping(path = "/api/ignore-filters", produces = { Api.API_MIME_TYPE, "application/json" })
public class IgnoreFiltersController {

    @Autowired
    private IgnoreFilters ignoreFilters;

    @Autowired
    private IgnoreFilterService ignoreFilterService;

    @Autowired
    private ValidatedRpkiObjects validatedRpkiObjects;

    @GetMapping
    public ResponseEntity<ApiResponse<Stream<IgnoreFilter>>> list(
            @RequestParam(name = "startFrom", defaultValue = "0") int startFrom,
            @RequestParam(name = "pageSize", defaultValue = "20") int pageSize,
            @RequestParam(name = "search", defaultValue = "", required = false) String searchString,
            @RequestParam(name = "sortBy", defaultValue = "prefix") String sortBy,
            @RequestParam(name = "sortDirection", defaultValue = "asc") String sortDirection) {

        final SearchTerm searchTerm = StringUtils.isNotBlank(searchString) ? new SearchTerm(searchString) : null;
        final Sorting sorting = Sorting.parse(sortBy, sortDirection);
        final Paging paging = Paging.of(startFrom, pageSize);

        final List<net.ripe.rpki.validator3.domain.IgnoreFilter> matching = ignoreFilters.find(searchTerm, sorting, paging).collect(Collectors.toList());

        int totalSize = (int) ignoreFilters.count(searchTerm);

        final Links links = Paging.links(
                startFrom, pageSize, totalSize,
                (sf, ps) -> methodOn(IgnoreFiltersController.class).list(sf, ps, searchString, sortBy, sortDirection));

        return ResponseEntity.ok(
                ApiResponse.<Stream<IgnoreFilter>>builder()
                        .links(links)
                        .metadata(Metadata.of(totalSize))
                        .data(matching.stream().map(this::toIgnoreFilter))
                        .build()
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<IgnoreFilter>> get(@PathVariable long id) {
        return ResponseEntity.ok(ignoreFilterResource(ignoreFilters.get(id)));
    }

    @PostMapping(consumes = { Api.API_MIME_TYPE, "application/json" })
    public ResponseEntity<ApiResponse<IgnoreFilter>> add(@RequestBody @Valid ApiCommand<AddIgnoreFilter> command) {
        final long id = ignoreFilterService.execute(command.getData());
        final net.ripe.rpki.validator3.domain.IgnoreFilter ignoreFilter = ignoreFilters.get(id);
        final Link selfRel = linkTo(methodOn(IgnoreFiltersController.class).get(id)).withSelfRel();
        return ResponseEntity.created(URI.create(selfRel.getHref())).body(ignoreFilterResource(ignoreFilter));
    }

    @DeleteMapping(path = "/{id}")
    public ResponseEntity<?> delete(@PathVariable long id) {
        ignoreFilterService.remove(id);
        return ResponseEntity.noContent().build();
    }

    private IgnoreFilter toIgnoreFilter(net.ripe.rpki.validator3.domain.IgnoreFilter f) {
        final IgnoreFiltersPredicate ignoreFiltersPredicate = new IgnoreFiltersPredicate(Stream.of(f));
        final Stream<ObjectController.RoaPrefix> affectedRoas = validatedRpkiObjects
                .findCurrentlyValidatedRoaPrefixes(null, null, null)
                .getObjects()
                .filter(roa -> ignoreFiltersPredicate.test(roa))
                .map(prefix -> new ObjectController.RoaPrefix(
                        String.valueOf(prefix.getAsn()),
                        prefix.getPrefix().toString(),
                        prefix.getEffectiveLength(),
                        null
                ));
        return new IgnoreFilter(f.getId(), f.getAsn(), f.getPrefix(), f.getComment(), affectedRoas);
    }

    private ApiResponse<IgnoreFilter> ignoreFilterResource(net.ripe.rpki.validator3.domain.IgnoreFilter ignoreFilter) {
        return ApiResponse.<IgnoreFilter>builder().data(toIgnoreFilter(ignoreFilter)).build();
    }
}
