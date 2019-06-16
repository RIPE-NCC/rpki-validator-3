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
package net.ripe.rpki.validator3.api.slurm;

import com.google.common.base.Charsets;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.multipart.CompletedFileUpload;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.validator3.api.Api;
import net.ripe.rpki.validator3.api.ApiError;
import net.ripe.rpki.validator3.api.ApiResponse;
import net.ripe.rpki.validator3.api.slurm.dtos.Slurm;
import net.ripe.rpki.validator3.api.trustanchors.TrustAnchorResource;
import net.ripe.rpki.validator3.storage.encoding.GsonCoder;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import javax.inject.Inject;
import java.util.Locale;

@Controller("/api/slurm")
@Produces({Api.API_MIME_TYPE, "application/json"})
@Slf4j
public class SlurmController {

    @Inject
    private SlurmService slurmService;

    @Post(value = "/upload", consumes = "multipart/form-data")
    public ResponseEntity<ApiResponse<TrustAnchorResource>> add(@QueryValue("file") CompletedFileUpload trustAnchorLocator,
                                                                Locale locale) {
        try {
            final String contents = new String(trustAnchorLocator.getBytes(), Charsets.UTF_8);
            slurmService.process(GsonCoder.getPrettyGson().fromJson(contents, Slurm.class));
            return ResponseEntity.noContent().build();
        } catch (Exception ex) {
            return ResponseEntity.badRequest().body(ApiResponse.error(ApiError.of(
                    HttpStatus.BAD_REQUEST,
                    "Invalid SLURM file: " + ex.getMessage()
            )));
        }
    }

    @Get
    public ResponseEntity<Slurm> slurm() {
        return ResponseEntity.ok(slurmService.get());
    }

    // FIXME Do something to force browser's save file prompt instead of rendering JSON
    @Get("/download")
    @Produces(Api.API_MIME_TYPE)
    public StreamingResponseBody download() {
        return out -> slurmService.writeTo(out);
    }
}
