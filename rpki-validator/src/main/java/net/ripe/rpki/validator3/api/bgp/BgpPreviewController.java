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
package net.ripe.rpki.validator3.api.bgp;

import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Produces;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.ripe.ipresource.Asn;
import net.ripe.ipresource.IpRange;
import net.ripe.rpki.validator3.api.Api;
import net.ripe.rpki.validator3.api.ApiResponse;
import net.ripe.rpki.validator3.api.Metadata;
import net.ripe.rpki.validator3.api.Paging;
import net.ripe.rpki.validator3.api.SearchTerm;
import net.ripe.rpki.validator3.api.Sorting;
import org.apache.commons.lang.StringUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import io.micronaut.http.annotation.QueryValue;

import javax.inject.Inject;
import java.util.function.Supplier;
import java.util.stream.Stream;

@Slf4j
@Controller( "/api/bgp")
@Produces( {Api.API_MIME_TYPE, "application/json"})
public class BgpPreviewController {

    @Inject
    private BgpPreviewService bgpPreviewService;

    @Get("/")
    public ResponseEntity<ApiResponse<Stream<BgpPreview>>> list(
            @QueryValue(value="startFrom", defaultValue = "0") long startFrom,
            @QueryValue(value = "pageSize", defaultValue = "20") long pageSize,
            //TODO: required false?
            @QueryValue(value = "search", defaultValue = "") String searchString,
            @QueryValue(value = "sortBy", defaultValue = "prefix") String sortBy,
            @QueryValue(value = "sortDirection", defaultValue = "asc") String sortDirection
    ) {
        final SearchTerm searchTerm = StringUtils.isNotBlank(searchString) ? new SearchTerm(searchString) : null;
        final Sorting sorting = Sorting.parse(sortBy, sortDirection);
        final Paging paging = Paging.of(startFrom, pageSize);

        BgpPreviewService.BgpPreviewResult bgpPreviewResult = bgpPreviewService.find(searchTerm, sorting, paging);

        return ResponseEntity.ok(ApiResponse.<Stream<BgpPreview>>builder()
                .data(bgpPreviewResult.getData().map(entry -> BgpPreview.of(
                        entry.getOrigin().toString(),
                        entry.getPrefix().toString(),
                        entry.getValidity().name()
                )))
                .metadata(Metadata.of(bgpPreviewResult.getTotalCount(), bgpPreviewResult.getLastModified()))
                .build());
    }

    @Get( "/validity")
    public ResponseEntity<ApiResponse<BgpPreviewService.BgpValidityWithFilteredResource>> validity(
            @QueryValue(value = "prefix") String prefix,
            @QueryValue(value = "asn") String asn
    ) {
        final BgpPreviewService.BgpValidityWithFilteredResource bgp = bgpPreviewService.validity(
                arg(() -> Asn.parse(asn)),
                arg(() -> IpRange.parse(prefix))
        );
        return ResponseEntity.ok(ApiResponse.<BgpPreviewService.BgpValidityWithFilteredResource>builder()
                .data(bgp)
                .metadata(Metadata.of(bgp.getValidatingRoas().size()))
                .build());
    }

    private static <T> T arg(Supplier<T> s) {
        try  {
            return s.get();
        } catch (Exception e) {
            throw new HttpMessageNotReadableException(e.getMessage());
        }
    }

    @Value(staticConstructor = "of")
    public static class BgpPreview {
        private String asn;
        private String prefix;
        private String validity;
    }
}
