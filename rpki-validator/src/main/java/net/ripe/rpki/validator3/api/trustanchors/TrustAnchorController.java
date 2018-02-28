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
import net.ripe.rpki.validator3.domain.RpkiRepositories;
import net.ripe.rpki.validator3.domain.RpkiRepository;
import net.ripe.rpki.validator3.domain.TrustAnchor;
import net.ripe.rpki.validator3.domain.TrustAnchorValidationRun;
import net.ripe.rpki.validator3.domain.TrustAnchors;
import net.ripe.rpki.validator3.domain.ValidationRun;
import net.ripe.rpki.validator3.domain.ValidationRuns;
import net.ripe.rpki.validator3.util.TrustAnchorExtractorException;
import net.ripe.rpki.validator3.util.TrustAnchorLocator;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.Links;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

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

@RestController
@RequestMapping(path = "/api/trust-anchors", produces = { Api.API_MIME_TYPE, "application/json" })
@Slf4j
public class TrustAnchorController {

    @Autowired
    private TrustAnchors trustAnchorRepository;
    @Autowired
    private TrustAnchorService trustAnchorService;
    @Autowired
    private ValidationRuns validationRunRepository;
    @Autowired
    private RpkiRepositories rpkiRepositories;
    @Autowired
    private MessageSource messageSource;

    @GetMapping
    public ResponseEntity<ApiResponse<List<TrustAnchorResource>>> list(Locale locale) {
        return ResponseEntity.ok(ApiResponse.data(
            new Links(linkTo(methodOn(TrustAnchorController.class).list(locale)).withSelfRel()),
            trustAnchorRepository.findAll()
                .stream()
                .map(ta -> TrustAnchorResource.of(ta, locale))
                .collect(Collectors.toList())
        ));
    }

    @PostMapping(consumes = { Api.API_MIME_TYPE, "application/json" })
    public ResponseEntity<ApiResponse<TrustAnchorResource>> add(@RequestBody @Valid ApiCommand<AddTrustAnchor> command, Locale locale) {
        long id = trustAnchorService.execute(command.getData());
        TrustAnchor trustAnchor = trustAnchorRepository.get(id);
        Link selfRel = linkTo(methodOn(TrustAnchorController.class).get(id, locale)).withSelfRel();
        return ResponseEntity.created(URI.create(selfRel.getHref())).body(trustAnchorResource(trustAnchor, locale));
    }

    @PostMapping(path = "/upload", consumes = "multipart/form-data")
    public ResponseEntity<ApiResponse<TrustAnchorResource>> add(@RequestParam("file") MultipartFile trustAnchorLocator, Locale locale) {
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
            TrustAnchor trustAnchor = trustAnchorRepository.get(id);
            Link selfRel = linkTo(methodOn(TrustAnchorController.class).get(id, locale)).withSelfRel();
            return ResponseEntity.created(URI.create(selfRel.getHref())).body(trustAnchorResource(trustAnchor, locale));
        } catch (TrustAnchorExtractorException ex) {
            return ResponseEntity.badRequest().body(ApiResponse.error(ApiError.of(
                HttpStatus.BAD_REQUEST,
                "Invalid trust anchor locator: " + ex.getMessage()
            )));
        }
    }

    @GetMapping(path = "/{id}")
    public ResponseEntity<ApiResponse<TrustAnchorResource>> get(@PathVariable long id, Locale locale) {
        TrustAnchor trustAnchor = trustAnchorRepository.get(id);
        return ResponseEntity.ok(trustAnchorResource(trustAnchor, locale));
    }

    @GetMapping(path = "/{id}/validation-run")
    public ResponseEntity<ApiResponse<ValidationRunResource>> validationResults(@PathVariable long id, HttpServletResponse response, Locale locale) throws IOException {
        TrustAnchor trustAnchor = trustAnchorRepository.get(id);
        ValidationRun validationRun = validationRunRepository.findLatestCompletedForTrustAnchor(trustAnchor)
            .orElseThrow(() -> new EmptyResultDataAccessException("latest validation run for trust anchor " + id, 1));
        response.sendRedirect(linkTo(methodOn(ValidationRunController.class).get(validationRun.getId(), locale)).toString());
        return null;
    }

    @GetMapping(path = "/{id}/validation-checks")
    public ResponseEntity<ApiResponse<TrustAnchorValidationChecksResource>> validationChecks(
        @PathVariable long id,
        @RequestParam(name = "startFrom", defaultValue = "0") int startFrom,
        @RequestParam(name = "pageSize", defaultValue = "20") int pageSize,
        @RequestParam(name = "search", required = false) String searchString,
        @RequestParam(name = "sortBy", defaultValue = "location") String sortBy,
        @RequestParam(name = "sortDirection", defaultValue = "asc") String sortDirection,
        Locale locale
    ) {
        final SearchTerm searchTerm = StringUtils.isNotBlank(searchString) ? new SearchTerm(searchString) : null;
        final Sorting sorting = Sorting.parse(sortBy, sortDirection);
        final Paging paging = Paging.of(startFrom, pageSize);

        int totalCount = validationRunRepository.countValidationChecksForValidationRun(id, searchTerm);

        Stream<ValidationCheckResource> checks = validationRunRepository.findValidationChecksForValidationRun(id, paging, searchTerm, sorting)
            .map(check -> ValidationCheckResource.of(check, messageSource.getMessage(check, locale)));

        Links links = Paging.links(startFrom, pageSize, totalCount,
                (sf, ps) -> methodOn(TrustAnchorController.class).validationChecks(id, sf, ps, searchString, sortBy, sortDirection, locale));

        return ResponseEntity.ok(ApiResponse.<TrustAnchorValidationChecksResource>builder()
            .links(links)
            .data(TrustAnchorValidationChecksResource.of(checks))
            .metadata(Metadata.of(totalCount))
            .build()
        );
    }

    @GetMapping(path = "/statuses")
    public ApiResponse<List<TaStatus>> statuses(HttpServletResponse response) {
        return ApiResponse.<List<TaStatus>>builder().data(trustAnchorRepository.getStatuses()).build();
    }

    @DeleteMapping(path = "/{id}")
    public ResponseEntity<?> delete(@PathVariable long id) {
        trustAnchorService.remove(id);
        return ResponseEntity.noContent().build();
    }

    private ApiResponse<TrustAnchorResource> trustAnchorResource(TrustAnchor trustAnchor, Locale locale) {
        Optional<TrustAnchorValidationRun> validationRun = validationRunRepository.findLatestCompletedForTrustAnchor(trustAnchor);
        ArrayList<Object> includes = new ArrayList<>(1);
        validationRun.ifPresent(run -> includes.add(ValidationRunResource.of(run, messageSource, locale)));
        return ApiResponse.<TrustAnchorResource>builder().data(
            TrustAnchorResource.of(trustAnchor, locale)
        ).includes(includes).build();
    }
}
