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
package net.ripe.rpki.validator3.api.roas;

import au.com.bytecode.opencsv.CSVWriter;
import io.swagger.annotations.ApiModelProperty;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.validator3.api.ApiResponse;
import net.ripe.rpki.validator3.api.trustanchors.TrustAnchorResource;
import net.ripe.rpki.validator3.domain.RpkiObject;
import net.ripe.rpki.validator3.domain.RpkiObjects;
import net.ripe.rpki.validator3.domain.Settings;
import net.ripe.rpki.validator3.domain.TrustAnchor;
import net.ripe.rpki.validator3.domain.TrustAnchors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.Links;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Controller to export validated ROA prefix information.
 * <p>
 * The data format is backwards compatible with the RPKI validator 2.x (see
 * https://github.com/RIPE-NCC/rpki-validator/blob/350d939d5e18858ee6cefc0c9a99e0c70b609b6d/rpki-validator-app/src/main/scala/net/ripe/rpki/validator/controllers/ExportController.scala#L41).
 */
@RestController
@Slf4j
public class ExportsController {

    @Autowired
    private TrustAnchors trustAnchors;

    @Autowired
    private RpkiObjects rpkiObjects;

    @Autowired
    private Settings settings;

    @GetMapping(path = "/export.json", produces = "text/json; charset=UTF-8")
    public ApiResponse<JsonExport> exportJson() {
        Map<Long, TrustAnchorResource> trustAnchorsById = trustAnchors.findAll().stream().collect(Collectors.toMap(ta -> ta.getId(), TrustAnchorResource::of));

        Stream<JsonRoaPrefix> validatedPrefixes = rpkiObjects
            .findCurrentlyValidated(RpkiObject.Type.ROA)
            .flatMap(pair -> {
                Link trustAnchorLink = trustAnchorsById.get(pair.getKey().getTrustAnchor().getId()).getLinks().getLink("self");
                return pair.getValue().getRoaPrefixes().stream()
                        .map(prefix -> new JsonRoaPrefix(
                            String.valueOf(prefix.getAsn()),
                            prefix.getPrefix(),
                            prefix.getEffectiveLength(),
                            new Links(trustAnchorLink.withRel(TrustAnchor.TYPE))
                        ));
                }
            )
            .distinct();

        return ApiResponse.<JsonExport>builder()
            .data(new JsonExport(settings.isInitialValidationRunCompleted(), trustAnchorsById.values(), validatedPrefixes))
            .build();
    }

    @GetMapping(path = "/export.csv", produces = "text/csv; charset=UTF-8")
    public void exportCsv(HttpServletResponse response) throws IOException {
        if (!settings.isInitialValidationRunCompleted()) {
            response.setStatus(HttpServletResponse.SC_NO_CONTENT);
            return;
        }

        response.setContentType("text/csv; charset=UTF-8");
        try (CSVWriter writer = new CSVWriter(response.getWriter())) {
            writer.writeNext(new String[]{"ASN", "IP Prefix", "Max Length", "Trust Anchor"});
            Stream<CsvRoaPrefix> validatedPrefixes = rpkiObjects
                .findCurrentlyValidated(RpkiObject.Type.ROA)
                .flatMap(pair -> pair.getValue().getRoaPrefixes().stream()
                    .map(prefix -> new CsvRoaPrefix(
                        String.valueOf(prefix.getAsn()),
                        prefix.getPrefix(),
                        prefix.getEffectiveLength(),
                        pair.getKey().getTrustAnchor().getName()
                    ))
                )
                .distinct();
            validatedPrefixes.forEach(prefix -> {
                writer.writeNext(new String[]{
                    prefix.getAsn(),
                    prefix.getPrefix(),
                    String.valueOf(prefix.getMaxLength()),
                    prefix.getTrustAnchorName()
                });
            });
        }
    }

    @Value
    public static class CsvRoaPrefix {
        private String asn;
        private String prefix;
        private int maxLength;
        private String trustAnchorName;
    }

    @Value
    public static class JsonExport {
        @ApiModelProperty(position = 1)
        boolean ready;
        @ApiModelProperty(position = 2)
        Collection<TrustAnchorResource> trustAnchors;
        @ApiModelProperty(position = 3)
        Stream<JsonRoaPrefix> roaPrefixes;
    }

    @Value
    public static class JsonRoaPrefix {
        private String asn;
        private String prefix;
        private int maxLength;
        private Links links;
    }
}
