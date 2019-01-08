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
package net.ripe.rpki.validator3.api.rpkirepositories;

import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.validator3.api.Api;
import net.ripe.rpki.validator3.api.ApiResponse;
import net.ripe.rpki.validator3.api.Metadata;
import net.ripe.rpki.validator3.api.Paging;
import net.ripe.rpki.validator3.api.SearchTerm;
import net.ripe.rpki.validator3.api.Sorting;
import net.ripe.rpki.validator3.domain.RpkiRepositories;
import net.ripe.rpki.validator3.domain.RpkiRepository;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.Links;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.stream.Stream;

import static org.springframework.hateoas.mvc.ControllerLinkBuilder.methodOn;

@RestController
@RequestMapping(path = "/api/rpki-repositories", produces = { Api.API_MIME_TYPE, "application/json" })
@Slf4j
public class RpkiRepositoriesController {

    private final RpkiRepositories rpkiRepositories;

    @Autowired
    public RpkiRepositoriesController(RpkiRepositories rpkiRepositories) {
        this.rpkiRepositories = rpkiRepositories;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Stream<RpkiRepositoryResource>>> list(
            @RequestParam(name = "status", required = false) RpkiRepository.Status status,
            @RequestParam(name = "ta", required = false) Long taId,@RequestParam(name = "startFrom", defaultValue = "0") long startFrom,
            @RequestParam(name = "pageSize", defaultValue = "20") long pageSize,
            @RequestParam(name = "search", defaultValue = "", required = false) String searchString,
            @RequestParam(name = "sortBy", defaultValue = "location") String sortBy,
            @RequestParam(name = "sortDirection", defaultValue = "asc") String sortDirection,
            @RequestParam(name = "hideChildrenOfDownloadedParent", defaultValue = "true") boolean hideChildrenOfDownloadedParent
    ) {
        final SearchTerm searchTerm = StringUtils.isNotBlank(searchString) ? new SearchTerm(searchString) : null;
        final Sorting sorting = Sorting.parse(sortBy, sortDirection);
        final Paging paging = Paging.of(startFrom, pageSize);
        final Stream<RpkiRepository> repositories = rpkiRepositories.findAll(status, taId, hideChildrenOfDownloadedParent, searchTerm, sorting, paging);

        final int totalSize = (int) rpkiRepositories.countAll(status, taId, hideChildrenOfDownloadedParent, searchTerm);
        final Links links = Paging.links(
                startFrom, pageSize, totalSize,
                (sf, ps) -> methodOn(RpkiRepositoriesController.class).list(status, taId, sf, ps, searchString, sortBy, sortDirection, hideChildrenOfDownloadedParent));

        final Stream<RpkiRepositoryResource> data = repositories.map(RpkiRepositoryResource::of);

        return ResponseEntity.ok(ApiResponse.<Stream<RpkiRepositoryResource>>builder()
                .data(data)
                .links(links)
                .metadata(Metadata.of(totalSize, System.currentTimeMillis()))
                .build());
    }

    @GetMapping(path = "/{id}")
    public ResponseEntity<ApiResponse<RpkiRepositoryResource>> get(@PathVariable long id) {
        RpkiRepository rpkiRepository = rpkiRepositories.get(id);
        return ResponseEntity.ok(ApiResponse.data(RpkiRepositoryResource.of(rpkiRepository)));
    }

    @GetMapping(path = "/statuses/{taId}")
    public ApiResponse<RepositoriesStatus> repositories(
            @PathVariable long taId,
            @RequestParam(name = "hideChildrenOfDownloadedParent", defaultValue = "true") boolean hideChildrenOfDownloadedParent
    ) {
        final Map<RpkiRepository.Status, Long> counts = rpkiRepositories.countByStatus(taId, hideChildrenOfDownloadedParent);

        return ApiResponse.<RepositoriesStatus>builder().data(RepositoriesStatus.of(
                counts.getOrDefault(RpkiRepository.Status.DOWNLOADED, 0L).intValue(),
                counts.getOrDefault(RpkiRepository.Status.PENDING, 0L).intValue(),
                counts.getOrDefault(RpkiRepository.Status.FAILED, 0L).intValue()
        )).build();
    }
}
