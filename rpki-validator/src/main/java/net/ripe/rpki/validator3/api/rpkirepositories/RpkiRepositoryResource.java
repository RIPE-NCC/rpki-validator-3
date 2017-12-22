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

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import net.ripe.rpki.validator3.api.Api;
import net.ripe.rpki.validator3.domain.RpkiRepository;
import net.ripe.rpki.validator3.domain.constraints.ValidLocationURI;
import org.springframework.hateoas.Links;

import javax.validation.constraints.NotNull;
import java.math.BigInteger;
import java.time.Instant;

import static org.springframework.hateoas.mvc.ControllerLinkBuilder.linkTo;
import static org.springframework.hateoas.mvc.ControllerLinkBuilder.methodOn;

@Data(staticConstructor = "of")
public class RpkiRepositoryResource {
    @ApiModelProperty(allowableValues = RpkiRepository.TYPE, required = true, position = 1)
    final String type;

    @ApiModelProperty(required = true, allowableValues = "range[" + Api.MINIMUM_VALID_ID + ",infinity]", example = "1", position = 2)
    final long id;

    @NotNull
    @ValidLocationURI
    final String locationURI;

    @NotNull
    final RpkiRepository.Status status;

    final Instant lastDownloadedAt;

    final String rrdpSessionId;

    final BigInteger rrdpSerial;

    final Links links;

    public static RpkiRepositoryResource of(RpkiRepository rpkiRepository) {
        return of(
            RpkiRepository.TYPE,
            rpkiRepository.getId(),
            rpkiRepository.getLocationUri(),
            rpkiRepository.getStatus(),
            rpkiRepository.getLastDownloadedAt(),
            rpkiRepository.getRrdpSessionId(),
            rpkiRepository.getRrdpSerial(),
            new Links(
                linkTo(methodOn(RpkiRepositoriesController.class).get(rpkiRepository.getId())).withSelfRel()
            )
        );
    }
}
