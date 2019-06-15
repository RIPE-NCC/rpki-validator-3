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
package net.ripe.rpki.validator3.api.validationruns;

import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Produces;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.validator3.api.Api;
import net.ripe.rpki.validator3.api.ApiResponse;
import net.ripe.rpki.validator3.storage.Storage;
import net.ripe.rpki.validator3.storage.data.validation.ValidationRun;
import net.ripe.rpki.validator3.storage.stores.TrustAnchors;
import net.ripe.rpki.validator3.storage.stores.ValidationRuns;
import org.springframework.context.MessageSource;
import org.springframework.hateoas.Links;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;

import javax.inject.Inject;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.springframework.hateoas.mvc.ControllerLinkBuilder.linkTo;
import static org.springframework.hateoas.mvc.ControllerLinkBuilder.methodOn;

@Controller( "/api/validation-runs")
@Produces( Api.API_MIME_TYPE)
@Slf4j
public class ValidationRunController {

    @Inject
    private ValidationRuns validationRuns;

    @Inject
    private TrustAnchors trustAnchors;

    @Inject
    private MessageSource messageSource;

    @Inject
    private Storage storage;

    @Get
    public ResponseEntity<ApiResponse<List<ValidationRunResource>>> list(Locale locale) {
        return storage.readTx(tx ->
                ResponseEntity.ok(ApiResponse.data(
                        new Links(linkTo(methodOn(ValidationRunController.class).list(locale)).withSelfRel()),
                        validationRuns.findAll(tx, ValidationRun.class)
                                .stream()
                                .map(validationRun -> ValidationRunResource.of(validationRun,
                                        vr -> validationRuns.getObjectCount(tx, vr),
                                        messageSource, locale))
                                .collect(Collectors.toList())
                )));
    }

    @Get( "/latest-successful")
    public ResponseEntity<ApiResponse<List<ValidationRunResource>>> listLatestSuccessful(Locale locale) {
        return storage.readTx(tx ->
                ResponseEntity.ok(ApiResponse.data(
                        new Links(linkTo(methodOn(ValidationRunController.class).listLatestSuccessful(locale)).withSelfRel()),
                        validationRuns.findLatestSuccessful(tx, ValidationRun.class)
                                .stream()
                                .map(validationRun -> ValidationRunResource.of(validationRun,
                                        vr -> validationRuns.getObjectCount(tx, vr),
                                        messageSource, locale))
                                .collect(Collectors.toList())
                )));
    }


    @Get( "/latest-completed-per-ta")
    public ResponseEntity<ApiResponse<List<ValidationRunResource>>> listLatestCompletedPerTa(Locale locale) {
        return storage.readTx(tx ->
                ResponseEntity.ok(ApiResponse.data(
                        new Links(linkTo(methodOn(ValidationRunController.class).listLatestCompletedPerTa(locale)).withSelfRel()),
                        trustAnchors.findAll(tx).stream().flatMap(ta ->
                                validationRuns.findLatestCaTreeValidationRun(tx, ta)
                                        .map(validationRun -> Stream.of(ValidationRunResource.of(validationRun,
                                                vr -> validationRuns.getObjectCount(tx, vr),
                                                messageSource, locale)))
                                        .orElse(Stream.empty()))
                                .collect(Collectors.toList())
                )));
    }

    @Get( "/{id}")
    public ResponseEntity<ApiResponse<ValidationRunResource>> get(@PathVariable long id, Locale locale) {
        return storage.readTx(tx ->
                validationRuns.get(tx, ValidationRun.class, id)
                        .map(validationRun ->
                                ResponseEntity.ok(ApiResponse.data(ValidationRunResource.of(validationRun,
                                        vr -> validationRuns.getObjectCount(tx, vr), messageSource, locale))))
                        .orElse(ResponseEntity.notFound().build()));
    }
}
