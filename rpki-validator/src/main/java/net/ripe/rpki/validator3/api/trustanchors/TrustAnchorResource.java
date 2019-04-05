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

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Value;
import net.ripe.rpki.validator3.api.Api;
import net.ripe.rpki.validator3.storage.data.TrustAnchor;
import org.springframework.hateoas.Links;

import java.util.List;
import java.util.Locale;

import static org.springframework.hateoas.mvc.ControllerLinkBuilder.linkTo;
import static org.springframework.hateoas.mvc.ControllerLinkBuilder.methodOn;

@Value(staticConstructor = "of")
@ApiModel(value = "TrustAnchor")
public class TrustAnchorResource {
    @ApiModelProperty(allowableValues = TrustAnchor.TYPE, required = true, position = 1)
    String type;
    @ApiModelProperty(required = true, allowableValues = "range[" + Api.MINIMUM_VALID_ID + ",infinity]", example = "1", position = 2)
    long id;
    @ApiModelProperty(required = true, example = "RPKI CA", position = 3)
    String name;
    @ApiModelProperty(required = true, position = 4)
    List<String> locations;
    @ApiModelProperty(required = true, position = 5)
    String subjectPublicKeyInfo;
    @ApiModelProperty(position = 6)
    String rsyncPrefetchUri;
    @ApiModelProperty(required = true, position = 7)
    boolean preconfigured;
    @ApiModelProperty(required = true, position = 8)
    boolean initialCertificateTreeValidationRunCompleted;
    @ApiModelProperty(position = 9)
    byte[] certificate;
    @ApiModelProperty(required = true, position = 10)
    Links links;

    public static TrustAnchorResource of(TrustAnchor trustAnchor, Locale locale) {
        return of(
            "trust-anchor",
            trustAnchor.getId().asLong(),
            trustAnchor.getName(),
            trustAnchor.getLocations(),
            trustAnchor.getSubjectPublicKeyInfo(),
            trustAnchor.getRsyncPrefetchUri(),
            trustAnchor.isPreconfigured(),
            trustAnchor.isInitialCertificateTreeValidationRunCompleted(),
            trustAnchor.getEncodedCertificate(),
            new Links(
                linkTo(methodOn(TrustAnchorController.class).get(trustAnchor.getId().asLong(), locale)).withSelfRel()
            )
        );
    }
}
