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
package net.ripe.rpki.validator3.api.trustanchors;

import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.annotation.QueryValue;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.validator3.api.Api;
import net.ripe.rpki.validator3.api.ApiCommand;
import net.ripe.rpki.validator3.api.ApiError;
import net.ripe.rpki.validator3.api.ApiResponse;
import net.ripe.rpki.validator3.api.Metadata;
import net.ripe.rpki.validator3.api.Paging;
import net.ripe.rpki.validator3.api.SearchTerm;
import net.ripe.rpki.validator3.api.Sorting;
import net.ripe.rpki.validator3.api.validationruns.ValidationCheckResource;
import net.ripe.rpki.validator3.api.validationruns.ValidationRunController;
import net.ripe.rpki.validator3.api.validationruns.ValidationRunResource;
import net.ripe.rpki.validator3.storage.Storage;
import net.ripe.rpki.validator3.storage.Tx;
import net.ripe.rpki.validator3.storage.data.Key;
import net.ripe.rpki.validator3.storage.data.TrustAnchor;
import net.ripe.rpki.validator3.storage.data.validation.TrustAnchorValidationRun;
import net.ripe.rpki.validator3.storage.stores.TrustAnchors;
import net.ripe.rpki.validator3.storage.stores.ValidationRuns;
import net.ripe.rpki.validator3.util.TrustAnchorExtractorException;
import net.ripe.rpki.validator3.util.TrustAnchorLocator;
import org.apache.commons.lang.StringUtils;
import org.springframework.context.MessageSource;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.Links;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.multipart.MultipartFile;

import javax.inject.Inject;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.springframework.hateoas.mvc.ControllerLinkBuilder.linkTo;
import static org.springframework.hateoas.mvc.ControllerLinkBuilder.methodOn;

@Slf4j
@Controller( "/api/trust-anchors")
@Produces( { Api.API_MIME_TYPE, "application/json" })

public class TrustAnchorController {

    @Inject
    private TrustAnchors trustAnchors;
    @Inject
    private TrustAnchorService trustAnchorService;
    @Inject
    private ValidationRuns validationRuns;
    @Inject
    private MessageSource messageSource;

    @Inject
    private Storage storage;

    @Get
    public ResponseEntity<ApiResponse<List<TrustAnchorResource>>> list(Locale locale) {
        return storage.readTx(tx -> ResponseEntity.ok(ApiResponse.data(
            new Links(linkTo(methodOn(TrustAnchorController.class).list(locale)).withSelfRel()),
            trustAnchors.findAll(tx)
                .stream()
                .map(ta -> TrustAnchorResource.of(ta, locale))
                .collect(Collectors.toList())
        )));
    }

    @Post(consumes = { Api.API_MIME_TYPE, "application/json" })
    public ResponseEntity<ApiResponse<TrustAnchorResource>> add(@RequestBody @Valid ApiCommand<AddTrustAnchor> command, Locale locale) {
        long id = trustAnchorService.execute(command.getData());
        return storage.readTx(tx -> {
            TrustAnchor trustAnchor = trustAnchors.get(tx, Key.of(id)).get();
            Link selfRel = linkTo(methodOn(TrustAnchorController.class).get(id, locale)).withSelfRel();
            return ResponseEntity.created(URI.create(selfRel.getHref())).body(trustAnchorResource(tx, trustAnchor, locale));
        });
    }

    @Post(value = "/upload", consumes = "multipart/form-data")
    public ResponseEntity<ApiResponse<TrustAnchorResource>> add(@QueryValue("file") MultipartFile trustAnchorLocator, Locale locale) {
        try {
            TrustAnchorLocator locator = TrustAnchorLocator.fromMultipartFile(trustAnchorLocator);
            AddTrustAnchor command = AddTrustAnchor.builder()
                .type(TrustAnchor.TYPE)
                .name(locator.getCaName())
                .locations(locator.getCertificateLocations().stream().map(URI::toASCIIString).collect(Collectors.toList()))
                .subjectPublicKeyInfo(locator.getPublicKeyInfo())
                .rsyncPrefetchUri(locator.getPrefetchUris().stream()
                    .filter(uri -> "rsync".equalsIgnoreCase(uri.getScheme()))
                    .map(URI::toASCIIString)
                    .findFirst().orElse(null)
                )
                .build();

            long id = trustAnchorService.execute(command);
            return storage.readTx(tx -> {
                Optional<TrustAnchor> trustAnchor = trustAnchors.get(tx, Key.of(id));
                Link selfRel = linkTo(methodOn(TrustAnchorController.class).get(id, locale)).withSelfRel();
                return ResponseEntity.created(URI.create(selfRel.getHref())).body(trustAnchorResource(tx, trustAnchor.get(), locale));
            });
        } catch (TrustAnchorExtractorException ex) {
            return ResponseEntity.badRequest().body(ApiResponse.error(ApiError.of(
                HttpStatus.BAD_REQUEST,
                "Invalid trust anchor locator: " + ex.getMessage()
            )));
        }
    }

    @Get( "/{id}")
    public ResponseEntity<ApiResponse<TrustAnchorResource>> get(long id, Locale locale) {
        return storage.readTx(tx ->
            trustAnchors.get(tx, Key.of(id))
                    .map(ta -> ResponseEntity.ok(trustAnchorResource(tx, ta, locale)))
                    .orElse(ResponseEntity.notFound().build()));
    }

    @Get( "/{id}/validation-run")
    public ResponseEntity<ApiResponse<ValidationRunResource>> validationResults(long id, HttpServletResponse response, Locale locale) throws IOException {
        Optional<TrustAnchorValidationRun> validationRun = storage.readTx(tx ->
                trustAnchors.get(tx, Key.of(id))
                        .flatMap(trustAnchor ->
                                validationRuns.findLatestCompletedForTrustAnchor(tx, trustAnchor)));

        if (validationRun.isPresent()) {
            response.sendRedirect(linkTo(methodOn(ValidationRunController.class).get(validationRun.get().key().asLong(), locale)).toString());
            return null;
        }
        return ResponseEntity.notFound().build();
    }

    @Get( "/{id}/validation-checks")
    public ResponseEntity<ApiResponse<Stream<ValidationCheckResource>>> validationChecks(
        long id,
        @QueryValue(value = "startFrom", defaultValue = "0") long startFrom,
        @QueryValue(value = "pageSize", defaultValue = "20") long pageSize,
        // TODO: required = false?
        @QueryValue(value = "search") String searchString,
        @QueryValue(value = "sortBy", defaultValue = "location") String sortBy,
        @QueryValue(value = "sortDirection", defaultValue = "asc") String sortDirection,
        Locale locale
    ) {
        final SearchTerm searchTerm = StringUtils.isNotBlank(searchString) ? new SearchTerm(searchString) : null;
        final Sorting sorting = Sorting.parse(sortBy, sortDirection);
        final Paging paging = Paging.of(startFrom, pageSize);

        return storage.readTx(tx -> {
            int totalCount = validationRuns.countValidationChecksForValidationRun(tx, id, searchTerm);

            Stream<ValidationCheckResource> checks = validationRuns.findValidationChecksForValidationRun(tx, id, paging, searchTerm, sorting)
                    .map(check -> ValidationCheckResource.of(check, messageSource.getMessage(check, locale)));

            Links links = Paging.links(startFrom, pageSize, totalCount,
                    (sf, ps) -> methodOn(TrustAnchorController.class).validationChecks(id, sf, ps, searchString, sortBy, sortDirection, locale));

            return ResponseEntity.ok(ApiResponse.<Stream<ValidationCheckResource>>builder()
                    .links(links)
                    .data(checks)
                    .metadata(Metadata.of(totalCount))
                    .build()
            );
        });
    }

    @Get( "/statuses")
    public ApiResponse<List<TaStatus>> statuses() {
        return storage.readTx(tx -> ApiResponse.<List<TaStatus>>builder().data(trustAnchors.getStatuses(tx)).build());
    }

    @Delete( "/{id}")
    public ResponseEntity<?> delete(long id) {
        trustAnchorService.remove(id);
        return ResponseEntity.noContent().build();
    }

    private ApiResponse<TrustAnchorResource> trustAnchorResource(Tx.Read tx, TrustAnchor trustAnchor, Locale locale) {
            Optional<TrustAnchorValidationRun> validationRun = validationRuns.findLatestCompletedForTrustAnchor(tx, trustAnchor);
            ArrayList<Object> includes = new ArrayList<>(1);
            validationRun.ifPresent(run -> includes.add(ValidationRunResource.of(run, vr -> validationRuns.getObjectCount(tx, vr), messageSource, locale)));
            return ApiResponse.<TrustAnchorResource>builder().data(
                    TrustAnchorResource.of(trustAnchor, locale)
            ).includes(includes).build();
    }
}
