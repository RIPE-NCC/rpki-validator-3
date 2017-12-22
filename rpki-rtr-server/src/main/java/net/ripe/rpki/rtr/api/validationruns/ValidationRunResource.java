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

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Builder;
import lombok.Data;
import net.ripe.rpki.rtr.api.trustanchors.TrustAnchorController;
import net.ripe.rpki.rtr.domain.CertificateTreeValidationRun;
import net.ripe.rpki.rtr.domain.RpkiRepositoryValidationRun;
import net.ripe.rpki.rtr.domain.RrdpRepositoryValidationRun;
import net.ripe.rpki.rtr.domain.RsyncRepositoryValidationRun;
import net.ripe.rpki.rtr.domain.TrustAnchor;
import net.ripe.rpki.rtr.domain.TrustAnchorValidationRun;
import net.ripe.rpki.rtr.domain.ValidationRun;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.Links;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.springframework.hateoas.mvc.ControllerLinkBuilder.linkTo;
import static org.springframework.hateoas.mvc.ControllerLinkBuilder.methodOn;

@Data
@Builder
@ApiModel(value = "ValidationRun")
public class ValidationRunResource {
    @ApiModelProperty(allowableValues = TrustAnchorValidationRun.TYPE + "," + RpkiRepositoryValidationRun.TYPE, required = true, position = 1)
    String type;

    Instant startedAt;

    Instant completedAt;

    String status;

    List<ValidationCheckResource> validationChecks;

    Integer validatedObjectCount;

    Integer addedObjectCount;

    Links links;

    public static ValidationRunResource of(ValidationRun validationRun) {
        List<Link> links = new ArrayList<>();
        links.add(linkTo(methodOn(ValidationRunController.class).get(validationRun.getId())).withSelfRel());

        ValidationRunResourceBuilder builder = ValidationRunResource.builder()
            .type(validationRun.getType())
            .startedAt(validationRun.getCreatedAt())
            .completedAt(validationRun.getCompletedAt())
            .status(validationRun.getStatus().name())
            .validationChecks(
                validationRun.getValidationChecks()
                    .stream()
                    .map(ValidationCheckResource::of)
                    .collect(Collectors.toList())
            );

        validationRun.visit(new ValidationRun.Visitor() {
            @Override
            public void accept(CertificateTreeValidationRun validationRun) {
                builder.validatedObjectCount(validationRun.getValidatedObjects().size());
                links.add(linkTo(methodOn(TrustAnchorController.class).get(validationRun.getTrustAnchor().getId())).withRel(TrustAnchor.TYPE));
            }

            @Override
            public void accept(RrdpRepositoryValidationRun validationRun) {
                builder.addedObjectCount(validationRun.getAddedObjectCount());
            }

            @Override
            public void accept(RsyncRepositoryValidationRun validationRun) {
                builder.addedObjectCount(validationRun.getAddedObjectCount());
            }

            @Override
            public void accept(TrustAnchorValidationRun validationRun) {
                links.add(linkTo(methodOn(TrustAnchorController.class).get(validationRun.getTrustAnchor().getId())).withRel(TrustAnchor.TYPE));
            }
        });

        return builder.links(new Links(links)).build();
    }
}
