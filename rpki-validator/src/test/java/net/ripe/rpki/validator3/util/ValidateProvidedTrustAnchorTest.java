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
package net.ripe.rpki.validator3.util;

import com.google.common.base.Joiner;
import com.google.common.collect.Sets;
import com.google.common.hash.Hashing;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.commons.validation.ValidationResult;
import net.ripe.rpki.validator3.IntegrationTest;
import net.ripe.rpki.validator3.domain.retrieval.TrustAnchorRetrievalService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Slf4j
@Setter
@IntegrationTest
public class ValidateProvidedTrustAnchorTest {
    @Value("classpath:packaging/generic/workdirs/preconfigured-tals/*.tal")
    private Resource[] tals;

    @Autowired
    private TrustAnchorRetrievalService trustAnchorRetrievalService;

    @Test
    public void testProvidedTrustAnchors() {
        // four included tals
        assert tals.length == 4;

        Arrays.stream(tals).forEach(this::validateTrustAnchor);
    }

    @SneakyThrows(IOException.class)
    private void validateTrustAnchor(Resource trustAnchor) {
        final TrustAnchorLocator tal = TrustAnchorLocator.fromFile(trustAnchor.getFile());

        // Create a set with the hashes of all the trust anchor certificates the tal refers to.
        final Set<String> certificateHashes = tal.getCertificateLocations()
                .parallelStream()
                .map(this::readCertificateFromUri)
                .map(cert -> Hashing.sha256().hashBytes(cert).toString())
                .collect(Collectors.toSet());

        log.info("certificate hash(es): {}", Joiner.on(", ").join(certificateHashes));

        // Should be identical
        assertEquals(certificateHashes.size(), 1);
    }

    @SneakyThrows(IOException.class)
    private byte[] readCertificateFromUri(URI uri) {
        ValidationResult res = ValidationResult.withLocation(uri);

        log.info("retrieving {}", uri.toASCIIString());

        final byte[] cert = trustAnchorRetrievalService.fetchTrustAnchorCertificate(uri, res);

        if (res.hasFailures()) {
            res.getFailuresForAllLocations().forEach(failure -> log.error("validation failure: {}", failure.toString()));
            throw new IllegalStateException(String.format("Failure while validating %s", uri.toASCIIString()));
        }
        return cert;
    }
}
