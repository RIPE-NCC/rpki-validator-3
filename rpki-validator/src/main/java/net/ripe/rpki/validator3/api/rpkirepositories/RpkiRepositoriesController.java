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

import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.annotation.QueryValue;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.validator3.api.Api;
import net.ripe.rpki.validator3.api.ApiResponse;
import net.ripe.rpki.validator3.api.Metadata;
import net.ripe.rpki.validator3.api.Paging;
import net.ripe.rpki.validator3.api.SearchTerm;
import net.ripe.rpki.validator3.api.Sorting;
import net.ripe.rpki.validator3.storage.Storage;
import net.ripe.rpki.validator3.storage.data.Key;
import net.ripe.rpki.validator3.storage.data.RpkiRepository;
import net.ripe.rpki.validator3.storage.stores.RpkiRepositories;
import org.apache.commons.lang.StringUtils;
import org.springframework.hateoas.Links;
import org.springframework.http.ResponseEntity;
import io.micronaut.http.annotation.Delete;

import javax.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.springframework.hateoas.mvc.ControllerLinkBuilder.methodOn;

@Slf4j
@Controller( "/api/rpki-repositories")
@Produces( {Api.API_MIME_TYPE, "application/json"})
public class RpkiRepositoriesController {

    private final RpkiRepositories rpkiRepositories;
    private final Storage storage;

    @Inject
    public RpkiRepositoriesController(RpkiRepositories rpkiRepositories, Storage storage) {
        this.rpkiRepositories = rpkiRepositories;
        this.storage = storage;
    }

    @Get
    public ResponseEntity<ApiResponse<Stream<RpkiRepositoryResource>>> list(
            //TODO: Required false
            @QueryValue(value = "status") RpkiRepository.Status status,
            //TODO: Required false
            @QueryValue(value = "ta") Long taId,
            @QueryValue(value = "startFrom", defaultValue = "0") long startFrom,
            @QueryValue(value = "pageSize", defaultValue = "20") long pageSize,
            //TODO: Required false
            @QueryValue(value = "search", defaultValue = "") String searchString,
            @QueryValue(value = "sortBy", defaultValue = "location") String sortBy,
            @QueryValue(value = "sortDirection", defaultValue = "asc") String sortDirection,
            @QueryValue(value = "hideChildrenOfDownloadedParent", defaultValue = "true") boolean hideChildrenOfDownloadedParent
    ) {
        final SearchTerm searchTerm = StringUtils.isNotBlank(searchString) ? new SearchTerm(searchString) : null;
        final Sorting sorting = Sorting.parse(sortBy, sortDirection);
        final Paging paging = Paging.of(startFrom, pageSize);

        return storage.readTx(tx -> {
            final Key taKey = taId == null ? null : Key.of(taId);
            final List<RpkiRepository> repositories = rpkiRepositories.findAll(tx,
                    status, taKey, hideChildrenOfDownloadedParent, searchTerm, sorting, paging)
                    .collect(Collectors.toList());

            final int totalSize = (int) rpkiRepositories.countAll(tx, status, taKey, hideChildrenOfDownloadedParent, searchTerm);
            final Links links = Paging.links(
                    startFrom, pageSize, totalSize,
                    (sf, ps) -> methodOn(RpkiRepositoriesController.class).list(status, taId, sf, ps, searchString, sortBy, sortDirection, hideChildrenOfDownloadedParent));

            final Stream<RpkiRepositoryResource> data = repositories.stream().map(RpkiRepositoryResource::of);

            return ResponseEntity.ok(ApiResponse.<Stream<RpkiRepositoryResource>>builder()
                    .data(data)
                    .links(links)
                    .metadata(Metadata.of(totalSize))
                    .build());
        });
    }

    @Get( "/{id}")
    public ResponseEntity<ApiResponse<RpkiRepositoryResource>> get(long id) {
        return storage.readTx(tx -> rpkiRepositories.get(tx, Key.of(id)))
                .map(r -> ResponseEntity.ok(ApiResponse.data(RpkiRepositoryResource.of(r))))
                .orElse(ResponseEntity.notFound().build());
    }

    @Get( "/statuses/{taId}")
    public ApiResponse<RepositoriesStatus> repositories(
            long taId,
            @QueryValue(value = "hideChildrenOfDownloadedParent", defaultValue = "true") boolean hideChildrenOfDownloadedParent
    ) {
        final Map<RpkiRepository.Status, Long> counts = storage.readTx(tx ->
                rpkiRepositories.countByStatus(tx, Key.of(taId), hideChildrenOfDownloadedParent));

        return ApiResponse.<RepositoriesStatus>builder().data(RepositoriesStatus.of(
                counts.getOrDefault(RpkiRepository.Status.DOWNLOADED, 0L).intValue(),
                counts.getOrDefault(RpkiRepository.Status.PENDING, 0L).intValue(),
                counts.getOrDefault(RpkiRepository.Status.FAILED, 0L).intValue()
        )).build();
    }

    @Delete( "/{id}")
    public ResponseEntity<?> delete(long id) {
        storage.writeTx0(tx -> rpkiRepositories.remove(tx, Key.of(id)));
        return ResponseEntity.noContent().build();
    }
}
