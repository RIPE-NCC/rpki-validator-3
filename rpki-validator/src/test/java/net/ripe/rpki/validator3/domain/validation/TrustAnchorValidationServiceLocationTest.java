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
package net.ripe.rpki.validator3.domain.validation;

import com.google.common.collect.ImmutableList;
import com.google.common.io.Resources;
import lombok.Setter;
import net.ripe.rpki.commons.crypto.x509cert.X509ResourceCertificate;
import net.ripe.rpki.validator3.IntegrationTest;
import net.ripe.rpki.validator3.domain.ErrorCodes;
import net.ripe.rpki.validator3.domain.retrieval.TrustAnchorRetrievalService;
import net.ripe.rpki.validator3.storage.data.TrustAnchor;
import net.ripe.rpki.validator3.storage.data.validation.TrustAnchorValidationRun;
import net.ripe.rpki.validator3.storage.data.validation.ValidationCheck;
import net.ripe.rpki.validator3.storage.stores.TrustAnchors;
import net.ripe.rpki.validator3.storage.stores.ValidationRuns;
import net.ripe.rpki.validator3.storage.stores.impl.GenericStorageTest;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Optional;

import static net.ripe.rpki.validator3.domain.validation.TrustAnchorValidationServiceTest.createRipeNccTrustAnchor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@IntegrationTest
@Setter
public class TrustAnchorValidationServiceLocationTest extends GenericStorageTest {
    private static final String DUMMY_RSYNC_URI = "rsync://rpki.example.org/ta/ta.cer";
    private static final String DUMMY_HTTPS_URI = "https://rpki.example.org/ta/ta.cer";

    @MockBean
    private TrustAnchorRetrievalService trustAnchorRetrievalService;

    @Autowired
    private TrustAnchorValidationService subject;

    @Autowired
    private TrustAnchors trustAnchors;

    @Autowired
    private ValidationRuns validationRuns;

    @Test
    public void testLocation_fallback_to_rsync() throws IOException {
        given(trustAnchorRetrievalService.fetchTrustAnchorCertificate(eq(URI.create(DUMMY_HTTPS_URI)), any()))
                .willReturn(null);
        given(trustAnchorRetrievalService.fetchTrustAnchorCertificate(eq(URI.create(DUMMY_RSYNC_URI)), any()))
                .willReturn(Resources.toByteArray(new ClassPathResource("ripe-ncc-ta.cer").getURL()));

        InOrder inOrder = Mockito.inOrder(trustAnchorRetrievalService);

        TrustAnchor ta = createRipeNccTrustAnchor();
        ta.setLocations(ImmutableList.of(DUMMY_RSYNC_URI, DUMMY_HTTPS_URI));
        wtx0(tx -> trustAnchors.add(tx, ta));

        subject.validate(ta.key().asLong());

        X509ResourceCertificate certificate = rtx(tx -> trustAnchors.get(tx, ta.key()).get().getCertificate());
        assertThat(certificate).isNotNull();

        Optional<TrustAnchorValidationRun> validationRun = rtx(tx -> validationRuns.findLatestCompletedForTrustAnchor(tx, ta));
        assertThat(validationRun).isPresent();
        assertThat(validationRun.get().isSucceeded()).isTrue();

        // Do not validate validation result: Warnings/errors were not added because the relevant code was mocked.

        then(trustAnchorRetrievalService).should(inOrder).fetchTrustAnchorCertificate(eq(URI.create(DUMMY_HTTPS_URI)), any());
        then(trustAnchorRetrievalService).should(inOrder).fetchTrustAnchorCertificate(eq(URI.create(DUMMY_RSYNC_URI)), any());
    }

    @Test
    public void testLocation_only_https_is_called_when_succeeds() throws IOException {
        final byte[] cert = Resources.toByteArray(new ClassPathResource("ripe-ncc-ta.cer").getURL());

        given(trustAnchorRetrievalService.fetchTrustAnchorCertificate(eq(URI.create(DUMMY_HTTPS_URI)), any()))
                .willReturn(cert);
        given(trustAnchorRetrievalService.fetchTrustAnchorCertificate(eq(URI.create(DUMMY_RSYNC_URI)), any()))
                .willReturn(cert);

        InOrder inOrder = Mockito.inOrder(trustAnchorRetrievalService);

        TrustAnchor ta = createRipeNccTrustAnchor();
        ta.setLocations(ImmutableList.of(DUMMY_RSYNC_URI, DUMMY_HTTPS_URI));
        wtx0(tx -> trustAnchors.add(tx, ta));

        subject.validate(ta.key().asLong());

        X509ResourceCertificate certificate = rtx(tx -> trustAnchors.get(tx, ta.key()).get().getCertificate());
        assertThat(certificate).isNotNull();

        Optional<TrustAnchorValidationRun> validationRun = rtx(tx -> validationRuns.findLatestCompletedForTrustAnchor(tx, ta));
        assertThat(validationRun).isPresent();
        assertThat(validationRun.get().isSucceeded()).isTrue();

        // Do not validate validation result: Warnings/errors were not added because the relevant code was mocked.

        then(trustAnchorRetrievalService).should(inOrder).fetchTrustAnchorCertificate(eq(URI.create(DUMMY_HTTPS_URI)), any());
        then(trustAnchorRetrievalService).shouldHaveNoMoreInteractions();
    }

    @Test
    public void testLocation_fails_when_all_locations_fail() throws IOException {
        given(trustAnchorRetrievalService.fetchTrustAnchorCertificate(eq(URI.create(DUMMY_HTTPS_URI)), any()))
                .willReturn(null);
        given(trustAnchorRetrievalService.fetchTrustAnchorCertificate(eq(URI.create(DUMMY_RSYNC_URI)), any()))
                .willReturn(null);

        InOrder inOrder = Mockito.inOrder(trustAnchorRetrievalService);

        TrustAnchor ta = createRipeNccTrustAnchor();
        ta.setLocations(ImmutableList.of(DUMMY_RSYNC_URI, DUMMY_HTTPS_URI));
        wtx0(tx -> trustAnchors.add(tx, ta));

        subject.validate(ta.key().asLong());

        X509ResourceCertificate certificate = rtx(tx -> trustAnchors.get(tx, ta.key()).get().getCertificate());
        assertThat(certificate).isNull();

        Optional<TrustAnchorValidationRun> validationRun = rtx(tx -> validationRuns.findLatestCompletedForTrustAnchor(tx, ta));
        assertThat(validationRun).isPresent();
        assertThat(validationRun.get().isFailed()).isTrue();

        final List<ValidationCheck> validationChecks = validationRun.get().getValidationChecks();
        assertThat(validationChecks).anyMatch(vc -> ErrorCodes.TRUST_ANCHOR_FETCH.equals(vc.getKey()) && ValidationCheck.Status.ERROR.equals(vc.getStatus()));

        then(trustAnchorRetrievalService).should(inOrder).fetchTrustAnchorCertificate(eq(URI.create(DUMMY_HTTPS_URI)), any());
        then(trustAnchorRetrievalService).should(inOrder).fetchTrustAnchorCertificate(eq(URI.create(DUMMY_RSYNC_URI)), any());
    }
}
