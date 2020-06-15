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
import net.ripe.rpki.commons.crypto.x509cert.X509ResourceCertificate;
import net.ripe.rpki.validator3.IntegrationTest;
import net.ripe.rpki.validator3.domain.ErrorCodes;
import net.ripe.rpki.validator3.domain.retrieval.TrustAnchorRetrievalService;
import net.ripe.rpki.validator3.storage.data.TrustAnchor;
import net.ripe.rpki.validator3.storage.data.validation.TrustAnchorValidationRun;
import net.ripe.rpki.validator3.storage.data.validation.ValidationCheck;
import net.ripe.rpki.validator3.storage.data.validation.ValidationRun;
import net.ripe.rpki.validator3.storage.stores.TrustAnchors;
import net.ripe.rpki.validator3.storage.stores.ValidationRuns;
import net.ripe.rpki.validator3.storage.stores.impl.GenericStorageTest;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(SpringRunner.class)
@IntegrationTest
public class TrustAnchorValidationServiceTest extends GenericStorageTest {

    private static final String DUMMY_RSYNC_URI = "rsync://localhost/non-existent/ta/ripe-ncc-ta.cer";

    @Autowired
    private TrustAnchors trustAnchors;

    @Autowired
    private TrustAnchorValidationService subject;

    @Autowired
    private ValidationRuns validationRuns;

    @Autowired
    private TrustAnchorRetrievalService trustAnchorRetrievalService;

    @Before
    public void init() {
        trustAnchorRetrievalService.setFileProtocolEnabled(true);
    }

    @Test
    public void test_success() throws IOException {
        TrustAnchor ta = createRipeNccTrustAnchor();
        wtx0(tx -> trustAnchors.add(tx, ta));

        ta.setLocations(ImmutableList.of(new ClassPathResource("ripe-ncc-ta.cer").getURI().toString()));
        subject.validate(ta.key().asLong());

        X509ResourceCertificate certificate = rtx(tx -> trustAnchors.get(tx, ta.key()).get().getCertificate());
        assertThat(certificate).isNotNull();

        Optional<TrustAnchorValidationRun> validationRun = rtx(tx -> validationRuns.findLatestCompletedForTrustAnchor(tx, ta));
        assertThat(validationRun).isPresent();
        assertThat(validationRun.get().getStatus()).isEqualTo(ValidationRun.Status.SUCCEEDED);

        assertThat(validationRun.get().getValidationChecks()).isEmpty();
    }

    @Test
    public void test_rsync_failure() {
        TrustAnchor ta = createRipeNccTrustAnchor();
        ta.setLocations(ImmutableList.of(DUMMY_RSYNC_URI));
        wtx0(tx -> trustAnchors.add(tx, ta));

        subject.validate(ta.key().asLong());

        assertThat(ta.getCertificate()).isNull();

        Optional<TrustAnchorValidationRun> validationRun = rtx(tx -> validationRuns.findLatestCompletedForTrustAnchor(tx, ta));
        assertThat(validationRun).isPresent();

        List<ValidationCheck> validationChecks = validationRun.get().getValidationChecks();
        assertThat(validationChecks).hasSize(2);

        assertThat(validationChecks).anyMatch(vc -> vc.getKey().equals(ErrorCodes.RSYNC_FETCH));
        assertThat(validationChecks).anyMatch(vc -> vc.getKey().equals(ErrorCodes.TRUST_ANCHOR_FETCH));
    }

    @Test
    public void test_empty_file() throws IOException {
        TrustAnchor ta = createRipeNccTrustAnchor();
        wtx0(tx -> trustAnchors.add(tx, ta));

        ta.setLocations(ImmutableList.of(new ClassPathResource("empty-file.cer").getURI().toString()));
        wtx0(tx -> trustAnchors.update(tx, ta));
        subject.validate(ta.key().asLong());

        X509ResourceCertificate certificate = rtx(tx -> trustAnchors.get(tx, ta.key()).get().getCertificate());
        assertThat(certificate).isNull();

        Optional<TrustAnchorValidationRun> validationRun = rtx(tx -> validationRuns.findLatestCompletedForTrustAnchor(tx, ta));
        assertThat(validationRun).isPresent();

        List<ValidationCheck> validationChecks = validationRun.get().getValidationChecks();
        assertThat(validationChecks).hasSize(1);
        assertThat(validationChecks.get(0).getKey()).isEqualTo(ErrorCodes.REPOSITORY_OBJECT_MINIMUM_SIZE);
    }

    @Test
    public void test_bad_subject_public_key() {
        TrustAnchor ta = createRipeNccTrustAnchor();
        ta.setSubjectPublicKeyInfo(ta.getSubjectPublicKeyInfo().toUpperCase());
        wtx0(tx -> trustAnchors.add(tx, ta));

        ta.setLocations(ImmutableList.of("src/test/resources/ripe-ncc-ta.cer"));
        subject.validate(ta.key().asLong());

        assertThat(ta.getCertificate()).isNull();

        Optional<TrustAnchorValidationRun> validationRun = rtx(tx -> validationRuns.findLatestCompletedForTrustAnchor(tx, ta));
        assertThat(validationRun).isPresent();

        List<ValidationCheck> validationChecks = validationRun.get().getValidationChecks();
        assertThat(validationChecks).hasSize(1);
        assertThat(validationChecks.get(0).getKey()).isEqualTo("trust.anchor.subject.key.matches.locator");
    }

    public static TrustAnchor createRipeNccTrustAnchor() {
        TrustAnchor ta = new TrustAnchor(false);
        ta.setName("RIPE NCC");
        ta.setLocations(ImmutableList.of("rsync://rpki.ripe.net/ta/ripe-ncc-ta.cer"));
        ta.setSubjectPublicKeyInfo("MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA0URYSGqUz2myBsOzeW1jQ6NsxNvlLMyhWknvnl8NiBCs/T/S2XuNKQNZ+wBZxIgPPV2pFBFeQAvoH/WK83HwA26V2siwm/MY2nKZ+Olw+wlpzlZ1p3Ipj2eNcKrmit8BwBC8xImzuCGaV0jkRB0GZ0hoH6Ml03umLprRsn6v0xOP0+l6Qc1ZHMFVFb385IQ7FQQTcVIxrdeMsoyJq9eMkE6DoclHhF/NlSllXubASQ9KUWqJ0+Ot3QCXr4LXECMfkpkVR2TZT+v5v658bHVs6ZxRD1b6Uk1uQKAyHUbn/tXvP8lrjAibGzVsXDT2L0x4Edx+QdixPgOji3gBMyL2VwIDAQAB");
        return ta;
    }
}
