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
package net.ripe.rpki.validator3.domain.retrieval;

import net.ripe.rpki.commons.validation.ValidationCheck;
import net.ripe.rpki.commons.validation.ValidationResult;
import net.ripe.rpki.validator3.domain.ErrorCodes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.net.URI;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
public class TrustAnchorRetrievalServiceTest {
    @Mock
    private TrustAnchorRetrievalService trustAnchorRetrievalService;

    private ValidationResult validationResult;

    @BeforeEach
    public void init() {
        validationResult = ValidationResult.withLocation(this.getClass().getName());
    }

    @Test
    public void testFetchTrustAnchorCertificate_rsync() throws Exception {

        given(trustAnchorRetrievalService.fetchRsyncTrustAnchorCertificate(any(), any())).willReturn(null);
        given(trustAnchorRetrievalService.fetchTrustAnchorCertificate(any(), any())).willCallRealMethod();

        final URI uri = URI.create("rsync://rsync.example.org/ta/ta.cer");

        trustAnchorRetrievalService.fetchTrustAnchorCertificate(uri, validationResult);

        then(trustAnchorRetrievalService).should().fetchRsyncTrustAnchorCertificate(uri, validationResult);

        final List<ValidationCheck> validationChecks = validationResult.getAllValidationChecksForCurrentLocation();
        assertThat(validationChecks).isEmpty();
    }

    @Test
    public void testFetchTrustAnchorCertificate_reject_http() throws Exception {
        given(trustAnchorRetrievalService.fetchTrustAnchorCertificate(any(), any())).willCallRealMethod();

        final URI uri = URI.create("http://rpki.example.org/ta/ta.cer");

        trustAnchorRetrievalService.fetchTrustAnchorCertificate(uri, validationResult);

        final List<ValidationCheck> validationChecks = validationResult.getAllValidationChecksForCurrentLocation();

        assertThat(validationChecks).hasSize(1);
        assertThat(validationChecks.get(0).getKey()).isEqualTo(ErrorCodes.TRUST_ANCHOR_FETCH);
    }

    @Test
    public void testFetchTrustAnchorCertificate_rejects_file_url() throws Exception {
        given(trustAnchorRetrievalService.fetchTrustAnchorCertificate(any(), any())).willCallRealMethod();

        final URI uri = new File("/tmp/does-not-exist.cer").toURI();

        trustAnchorRetrievalService.fetchTrustAnchorCertificate(uri, validationResult);

        final List<ValidationCheck> validationChecks = validationResult.getAllValidationChecksForCurrentLocation();

        assertThat(validationChecks).hasSize(1);
        assertThat(validationChecks.get(0).getKey()).isEqualTo(ErrorCodes.TRUST_ANCHOR_FETCH);
    }

    @Test
    public void testFetchTrustAnchorCertificate_fetches_https() throws Exception {
        given(trustAnchorRetrievalService.fetchTrustAnchorCertificate(any(), any())).willCallRealMethod();
        given(trustAnchorRetrievalService.fetchHttpsTrustAnchorCertificate(any(), any())).willReturn(null);

        final URI uri = URI.create("https://rpki.example.org/ta/ta.cer");

        trustAnchorRetrievalService.fetchTrustAnchorCertificate(uri, validationResult);

        then(trustAnchorRetrievalService).should().fetchHttpsTrustAnchorCertificate(uri, validationResult);

        final List<ValidationCheck> validationChecks = validationResult.getAllValidationChecksForCurrentLocation();
        assertThat(validationChecks).isEmpty();
    }
}
