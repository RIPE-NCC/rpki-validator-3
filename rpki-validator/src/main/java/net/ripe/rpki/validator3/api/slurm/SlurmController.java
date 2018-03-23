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
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.validator3.api.Api;
import net.ripe.rpki.validator3.api.ApiError;
import net.ripe.rpki.validator3.api.ApiResponse;
import net.ripe.rpki.validator3.api.slurm.dtos.Slurm;
import net.ripe.rpki.validator3.api.trustanchors.TrustAnchorResource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Locale;

@RestController
@RequestMapping(path = "/api/slurm", produces = {Api.API_MIME_TYPE, "application/json"})
@Slf4j
public class SlurmController {

    @Autowired
    private SlurmService slurmService;

    @PostMapping(path = "/upload", consumes = "multipart/form-data")
    public ResponseEntity<ApiResponse<TrustAnchorResource>> add(@RequestParam("file") MultipartFile trustAnchorLocator, Locale locale) {
        try {
            final String contents = new String(trustAnchorLocator.getBytes(), Charsets.UTF_8);
            slurmService.process(SlurmParser.parse(contents));
            return ResponseEntity.noContent().build();
        } catch (Exception ex) {
            return ResponseEntity.badRequest().body(ApiResponse.error(ApiError.of(
                    HttpStatus.BAD_REQUEST,
                    "Invalid SLURM file: " + ex.getMessage()
            )));
        }
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Slurm>> slurm() {
        return ResponseEntity.ok(
                ApiResponse.<Slurm>builder()
                        .data(slurmService.get()).build()
        );
    }

    // FIXME Do something to force browser's save file prompt instead of rendering JSON
    @GetMapping(path="/download", produces = Api.API_MIME_TYPE)
    public ResponseEntity<ApiResponse<Slurm>> download() {
        return slurm();
    }

}
