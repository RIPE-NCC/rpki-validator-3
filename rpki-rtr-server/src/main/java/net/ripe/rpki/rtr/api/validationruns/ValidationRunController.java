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
package net.ripe.rpki.rtr.api.validationruns;

import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.rtr.api.Api;
import net.ripe.rpki.rtr.api.ApiResponse;
import net.ripe.rpki.rtr.domain.ValidationRun;
import net.ripe.rpki.rtr.domain.ValidationRuns;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.Links;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

import static org.springframework.hateoas.mvc.ControllerLinkBuilder.linkTo;
import static org.springframework.hateoas.mvc.ControllerLinkBuilder.methodOn;

@RestController
@RequestMapping(path = "/validation-runs", produces = Api.API_MIME_TYPE)
@Slf4j
public class ValidationRunController {

    private final ValidationRuns validationRunRepository;

    @Autowired
    public ValidationRunController(ValidationRuns validationRunRepository) {
        this.validationRunRepository = validationRunRepository;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ValidationRunResource>>> list() {
        return ResponseEntity.ok(ApiResponse.data(
            new Links(linkTo(methodOn(ValidationRunController.class).list()).withSelfRel()),
            validationRunRepository.findAll(ValidationRun.class)
                .stream()
                .map(ValidationRunResource::of)
                .collect(Collectors.toList())
        ));
    }

    @GetMapping(path = "/latest")
    public ResponseEntity<ApiResponse<List<ValidationRunResource>>> listLatestSuccessful() {
        return ResponseEntity.ok(ApiResponse.data(
            new Links(linkTo(methodOn(ValidationRunController.class).listLatestSuccessful()).withSelfRel()),
            validationRunRepository.findLatestSuccessful(ValidationRun.class)
                .stream()
                .map(ValidationRunResource::of)
                .collect(Collectors.toList())
        ));
    }

    @GetMapping(path = "/{id}")
    public ResponseEntity<ApiResponse<ValidationRunResource>> get(@PathVariable long id) {
        ValidationRun validationRun = validationRunRepository.get(ValidationRun.class, id);
        return ResponseEntity.ok(ApiResponse.data(ValidationRunResource.of(validationRun)));
    }
}
