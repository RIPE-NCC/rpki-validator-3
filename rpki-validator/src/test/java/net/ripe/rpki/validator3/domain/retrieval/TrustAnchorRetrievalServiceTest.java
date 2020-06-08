package net.ripe.rpki.validator3.domain.retrieval;

import net.ripe.rpki.commons.validation.ValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.*;
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
        assertFalse(validationResult.hasFailures());
    }

    @Test
    public void testFetchTrustAnchorCertificate_reject_http() throws Exception {
        given(trustAnchorRetrievalService.fetchTrustAnchorCertificate(any(), any())).willCallRealMethod();

        final URI uri = URI.create("http://rpki.example.org/ta/ta.cer");

        trustAnchorRetrievalService.fetchTrustAnchorCertificate(uri, validationResult);

        assertTrue(validationResult.hasFailures());
    }

    @Test
    public void testFetchTrustAnchorCertificate_fetches_https() throws Exception {
        given(trustAnchorRetrievalService.fetchTrustAnchorCertificate(any(), any())).willCallRealMethod();
        given(trustAnchorRetrievalService.fetchHttpsTrustAnchorCertificate(any(), any())).willReturn(null);

        final URI uri = URI.create("https://rpki.example.org/ta/ta.cer");

        trustAnchorRetrievalService.fetchTrustAnchorCertificate(uri, validationResult);

        then(trustAnchorRetrievalService).should().fetchHttpsTrustAnchorCertificate(uri, validationResult);
        assertFalse(validationResult.hasFailures());
    }
}
