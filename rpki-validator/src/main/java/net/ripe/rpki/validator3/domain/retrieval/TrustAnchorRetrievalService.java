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

import com.google.common.io.Files;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.commons.validation.ValidationResult;
import net.ripe.rpki.validator3.api.util.BuildInformation;
import net.ripe.rpki.validator3.domain.ErrorCodes;
import net.ripe.rpki.validator3.domain.metrics.HttpClientMetricsService;
import net.ripe.rpki.validator3.domain.metrics.RsyncMetricsService;
import net.ripe.rpki.validator3.util.Rsync;
import net.ripe.rpki.validator3.util.RsyncFactory;
import org.apache.commons.lang3.ArrayUtils;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.http.HttpHeader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

@Slf4j
@Setter
@Service
public class TrustAnchorRetrievalService {
    private boolean isFileProtocolEnabled = false;

    @Value("${rpki.validator.rsync.local.storage.directory}")
    private File localRsyncStorageDirectory;

    @Autowired
    private BuildInformation buildInformation;

    @Autowired
    private RsyncFactory rsyncFactory;

    @Autowired
    private HttpClient httpClient;

    @Autowired
    private HttpClientMetricsService httpMetrics;

    @Autowired
    private RsyncMetricsService rsyncMetrics;

    public byte[] fetchTrustAnchorCertificate(URI trustAnchorCertificateURI, ValidationResult validationResult) throws IOException {
        switch (trustAnchorCertificateURI.getScheme()) {
            case "rsync":
                return fetchRsyncTrustAnchorCertificate(trustAnchorCertificateURI, validationResult);
            case "https":
                return fetchHttpsTrustAnchorCertificate(trustAnchorCertificateURI, validationResult);
            case "file":
                if (isFileProtocolEnabled) {
                    return Files.toByteArray(new File(trustAnchorCertificateURI));
                }
                validationResult.error(ErrorCodes.TRUST_ANCHOR_FETCH, trustAnchorCertificateURI.toASCIIString(), "File support not explicitly enabled.");
                return null;
            default:
                validationResult.error(ErrorCodes.TRUST_ANCHOR_FETCH, trustAnchorCertificateURI.toASCIIString(), "Unsupported URI");
                return null;
        }
    }

    protected byte[] fetchHttpsTrustAnchorCertificate(URI trustAnchorCertificateURI, ValidationResult validationResult) throws IOException {
        final long t0 = System.currentTimeMillis();
        String statusDescription = "unknown";

        try {
            ContentResponse res = httpClient
                    .newRequest(trustAnchorCertificateURI)
                    .header(HttpHeader.USER_AGENT, null)
                    .header(HttpHeader.USER_AGENT, String.format("RIPE NCC RPKI Validator/%s", buildInformation.getVersion()))
                    .send();
            log.debug("HTTP {} when fetching HTTPS trust anchor from {}", res.getStatus(), trustAnchorCertificateURI);
            statusDescription = String.valueOf(res.getStatus());
            if (res.getStatus() != 200) {
                validationResult.error(ErrorCodes.TRUST_ANCHOR_FETCH, trustAnchorCertificateURI.toASCIIString(), String.format("HTTP %d", res.getStatus()));
                return null;
            }

            return res.getContent();
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            validationResult.error(ErrorCodes.TRUST_ANCHOR_FETCH, trustAnchorCertificateURI.toASCIIString(), e.getMessage());
            statusDescription = HttpClientMetricsService.unwrapExceptionString(e);

            log.error("Error while loading trust anchor certificate over https", e);
            return null;
        } finally {
            httpMetrics.update(trustAnchorCertificateURI, statusDescription, System.currentTimeMillis()-t0);
        }
    }

    protected byte[] fetchRsyncTrustAnchorCertificate(URI trustAnchorCertificateURI, ValidationResult validationResult) throws IOException {
        final long t0 = System.currentTimeMillis();

        File targetFile = Rsync.localFileFromRsyncUri(localRsyncStorageDirectory, trustAnchorCertificateURI);
        if (targetFile.getParentFile().mkdirs()) {
            log.info("created local rsync storage directory {} for trust anchor {}", targetFile.getParentFile(), trustAnchorCertificateURI);
        }

        net.ripe.rpki.commons.rsync.Rsync rsync = rsyncFactory.rsyncFile(trustAnchorCertificateURI.toASCIIString(), targetFile.getPath());
        int exitStatus = rsync.execute();
        rsyncMetrics.update(trustAnchorCertificateURI, exitStatus, System.currentTimeMillis() - t0);

        if (exitStatus != 0) {
            validationResult.error(ErrorCodes.RSYNC_FETCH, String.valueOf(exitStatus), ArrayUtils.toString(rsync.getErrorLines()));
            return null;
        } else {
            log.info("Downloaded certificate {} to {}", trustAnchorCertificateURI, targetFile);
            return Files.toByteArray(targetFile);
        }
    }
}
