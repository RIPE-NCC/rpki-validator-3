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
import net.ripe.rpki.validator3.api.validationruns.ValidationRunController;
import net.ripe.rpki.validator3.api.validationruns.ValidationRunResource;
import net.ripe.rpki.validator3.domain.*;
import net.ripe.rpki.validator3.util.TrustAnchorExtractorException;
import net.ripe.rpki.validator3.util.TrustAnchorLocator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.Links;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.springframework.hateoas.mvc.ControllerLinkBuilder.linkTo;
import static org.springframework.hateoas.mvc.ControllerLinkBuilder.methodOn;

@RestController
@RequestMapping(path = "/trust-anchors", produces = Api.API_MIME_TYPE)
@Slf4j
public class TrustAnchorController {

    private final TrustAnchors trustAnchorRepository;
    private final TrustAnchorService trustAnchorService;
    private final ValidationRuns validationRunRepository;

    @Autowired
    public TrustAnchorController(TrustAnchors trustAnchorRepository, TrustAnchorService trustAnchorService, ValidationRuns validationRunRepository) {
        this.trustAnchorRepository = trustAnchorRepository;
        this.trustAnchorService = trustAnchorService;
        this.validationRunRepository = validationRunRepository;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<TrustAnchorResource>>> list() {
        return ResponseEntity.ok(ApiResponse.data(
            new Links(linkTo(methodOn(TrustAnchorController.class).list()).withSelfRel()),
            trustAnchorRepository.findAll()
                .stream()
                .map(TrustAnchorResource::of)
                .collect(Collectors.toList())
        ));
    }

    @PostMapping(consumes = Api.API_MIME_TYPE)
    public ResponseEntity<ApiResponse<TrustAnchorResource>> add(@RequestBody @Valid ApiCommand<AddTrustAnchor> command) {
        long id = trustAnchorService.execute(command.getData());
        TrustAnchor trustAnchor = trustAnchorRepository.get(id);
        Link selfRel = linkTo(methodOn(TrustAnchorController.class).get(id)).withSelfRel();
        return ResponseEntity.created(URI.create(selfRel.getHref())).body(trustAnchorResource(trustAnchor));
    }

    @PostMapping(path = "/upload", consumes = "multipart/form-data")
    public ResponseEntity<ApiResponse<TrustAnchorResource>> add(@RequestParam("file") MultipartFile trustAnchorLocator) throws IOException {
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
            Link selfRel = linkTo(methodOn(TrustAnchorController.class).get(id)).withSelfRel();
            return ResponseEntity.created(URI.create(selfRel.getHref())).body(trustAnchorResource(trustAnchor));
        } catch (TrustAnchorExtractorException ex) {
            return ResponseEntity.badRequest().body(ApiResponse.error(ApiError.of(
                HttpStatus.BAD_REQUEST,
                "Invalid trust anchor locator: " + ex.getMessage()
            )));
        }
    }

    @GetMapping(path = "/{id}")
    public ResponseEntity<ApiResponse<TrustAnchorResource>> get(@PathVariable long id) {
        TrustAnchor trustAnchor = trustAnchorRepository.get(id);
        return ResponseEntity.ok(trustAnchorResource(trustAnchor));
    }

    @GetMapping(path = "/{id}/validation-run")
    public ResponseEntity<ApiResponse<ValidationRunResource>> validationResults(@PathVariable long id, HttpServletResponse response) throws IOException {
        TrustAnchor trustAnchor = trustAnchorRepository.get(id);
        ValidationRun validationRun = validationRunRepository.findLatestCompletedForTrustAnchor(trustAnchor)
            .orElseThrow(() -> new EmptyResultDataAccessException("latest validation run for trust anchor " + id, 1));
        response.sendRedirect(linkTo(methodOn(ValidationRunController.class).get(validationRun.getId())).toString());
        return null;
    }

    @DeleteMapping(path = "/{id}")
    public ResponseEntity<?> delete(@PathVariable long id) {
        trustAnchorService.remove(id);
        return ResponseEntity.noContent().build();
    }

    private ApiResponse<TrustAnchorResource> trustAnchorResource(TrustAnchor trustAnchor) {
        Optional<TrustAnchorValidationRun> validationRun = validationRunRepository.findLatestCompletedForTrustAnchor(trustAnchor);
        ArrayList<Object> includes = new ArrayList<>(1);
        validationRun.ifPresent(run -> includes.add(ValidationRunResource.of(run)));
        return ApiResponse.<TrustAnchorResource>builder().data(
            TrustAnchorResource.of(trustAnchor)
        ).includes(includes).build();
    }
}
