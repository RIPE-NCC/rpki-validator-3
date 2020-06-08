package net.ripe.rpki.validator3.domain.retrieval;

import com.google.common.io.Files;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.commons.validation.ValidationResult;
import net.ripe.rpki.validator3.api.util.BuildInformation;
import net.ripe.rpki.validator3.domain.ErrorCodes;
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
    @Value("${rpki.validator.rsync.local.storage.directory}")
    private File localRsyncStorageDirectory;

    @Autowired
    private BuildInformation buildInformation;

    @Autowired
    private RsyncFactory rsyncFactory;

    @Autowired
    private HttpClient httpClient;

    public byte[] fetchTrustAnchorCertificate(URI trustAnchorCertificateURI, ValidationResult validationResult) throws IOException {
        switch (trustAnchorCertificateURI.getScheme()) {
            case "rsync":
                return fetchRsyncTrustAnchorCertificate(trustAnchorCertificateURI, validationResult);
            case "https":
                return fetchHttpsTrustAnchorCertificate(trustAnchorCertificateURI, validationResult);
            default:
                validationResult.error(ErrorCodes.TRUST_ANCHOR_FETCH, trustAnchorCertificateURI.toASCIIString(), "Unsupported URI");
                return null;
        }
    }

    protected byte[] fetchHttpsTrustAnchorCertificate(URI trustAnchorCertificateURI, ValidationResult validationResult) throws IOException {
        try {
            ContentResponse res = httpClient
                    .newRequest(trustAnchorCertificateURI)
                    .header(HttpHeader.USER_AGENT, null)
                    .header(HttpHeader.USER_AGENT, String.format("RIPE NCC RPKI Validator/%s", buildInformation.getVersion()))
                    .send();
            log.info("HTTP {} when fetching HTTPS trust anchor from {}", res.getStatus(), trustAnchorCertificateURI);

            if (res.getStatus() != 200) {
                validationResult.error(ErrorCodes.TRUST_ANCHOR_FETCH, trustAnchorCertificateURI.toASCIIString(), String.format("HTTP %d", res.getStatus()));
                return null;
            }

            return res.getContent();
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            validationResult.error(ErrorCodes.TRUST_ANCHOR_FETCH, trustAnchorCertificateURI.toASCIIString(), e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    protected byte[] fetchRsyncTrustAnchorCertificate(URI trustAnchorCertificateURI, ValidationResult validationResult) throws IOException {
        File targetFile = Rsync.localFileFromRsyncUri(localRsyncStorageDirectory, trustAnchorCertificateURI);
        if (targetFile.getParentFile().mkdirs()) {
            log.info("created local rsync storage directory {} for trust anchor {}", targetFile.getParentFile(), trustAnchorCertificateURI);
        }

        net.ripe.rpki.commons.rsync.Rsync rsync = rsyncFactory.rsyncFile(trustAnchorCertificateURI.toASCIIString(), targetFile.getPath());
        int exitStatus = rsync.execute();
        if (exitStatus != 0) {
            validationResult.error(ErrorCodes.RSYNC_FETCH, String.valueOf(exitStatus), ArrayUtils.toString(rsync.getErrorLines()));
            return null;
        } else {
            log.info("Downloaded certificate {} to {}", trustAnchorCertificateURI, targetFile);
            return Files.toByteArray(targetFile);
        }
    }
}
