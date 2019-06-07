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
package net.ripe.rpki.validator3.storage.stores.impl;

import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.validator3.IntegrationTest;
import net.ripe.rpki.validator3.TestObjects;
import net.ripe.rpki.validator3.storage.data.Ref;
import net.ripe.rpki.validator3.storage.data.TrustAnchor;
import net.ripe.rpki.validator3.storage.data.validation.CertificateTreeValidationRun;
import net.ripe.rpki.validator3.storage.data.validation.ValidationRun;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(SpringRunner.class)
@IntegrationTest
@Slf4j
public class LmdbValidationRunsTest extends GenericStorageTest {

    @Test
    public void testAddUpdate() {
        final TrustAnchor trustAnchor = TestObjects.newTrustAnchor();
        wtx0(tx -> this.getTrustAnchors().add(tx, trustAnchor));

        ValidationRun validationRun = wtx(tx -> {
            ValidationRun vr = new CertificateTreeValidationRun(this.getTrustAnchors().makeRef(tx, trustAnchor.key()));
            this.getValidationRuns().add(tx, vr);
            return vr;
        });

        rtx0(tx -> {
            CertificateTreeValidationRun actual = this.getValidationRuns().get(tx,
                    CertificateTreeValidationRun.class, validationRun.key().asLong()).get();
            assertEquals(validationRun, actual);
        });

        validationRun.setSucceeded();
        wtx0(tx -> this.getValidationRuns().update(tx, validationRun));

        rtx0(tx -> {
            CertificateTreeValidationRun actual = this.getValidationRuns().get(tx,
                    CertificateTreeValidationRun.class, validationRun.key().asLong()).get();
            assertEquals(validationRun, actual);
        });
    }

    @Test
    public void testLatestSuccessful() throws Exception {

        final TrustAnchor trustAnchor = TestObjects.newTrustAnchor();
        wtx0(tx -> this.getTrustAnchors().add(tx, trustAnchor));

        final Ref<TrustAnchor> trustAnchorRef = rtx(tx -> this.getTrustAnchors().makeRef(tx, trustAnchor.key()));
        ValidationRun vr1 = wtx(tx -> {
            ValidationRun vr = new CertificateTreeValidationRun(trustAnchorRef);
            vr.setSucceeded();
            this.getValidationRuns().add(tx, vr);
            return vr;
        });

        Thread.sleep(5);

        List<CertificateTreeValidationRun> rtx1 = rtx(tx -> this.getValidationRuns().findAll(tx, CertificateTreeValidationRun.class));

        ValidationRun vr2 = wtx(tx -> {
            ValidationRun vr = new CertificateTreeValidationRun(trustAnchorRef);
            vr.setSucceeded();
            this.getValidationRuns().add(tx, vr);
            return vr;
        });

        Thread.sleep(5);

        List<CertificateTreeValidationRun> rtx2 = rtx(tx -> this.getValidationRuns().findAll(tx, CertificateTreeValidationRun.class));

        ValidationRun vr3 = wtx(tx -> {
            ValidationRun vr = new CertificateTreeValidationRun(trustAnchorRef);
            vr.setFailed();
            this.getValidationRuns().add(tx, vr);
            return vr;
        });

        List<CertificateTreeValidationRun> rtx3 = rtx(tx -> this.getValidationRuns().findAll(tx, CertificateTreeValidationRun.class));

        rtx0(tx -> {
            List<CertificateTreeValidationRun> latestSuccessful = this.getValidationRuns().findLatestSuccessful(tx, CertificateTreeValidationRun.class);
            assertEquals(1, latestSuccessful.size());
            assertEquals(vr2, latestSuccessful.get(0));
        });

        rtx0(tx -> {
            Optional<CertificateTreeValidationRun> latestCompletedForTrustAnchor = this.getValidationRuns().findLatestCaTreeValidationRun(tx, trustAnchor);
            assertTrue(latestCompletedForTrustAnchor.isPresent());
            assertEquals(vr3, latestCompletedForTrustAnchor.get());
        });

        wtx0(tx -> {
            vr3.setCompletedAt(null);
            this.getValidationRuns().update(tx, vr3);
        });

        rtx0(tx -> {
            Optional<CertificateTreeValidationRun> latestCompletedForTrustAnchor = this.getValidationRuns().findLatestCaTreeValidationRun(tx, trustAnchor);
            assertTrue(latestCompletedForTrustAnchor.isPresent());
            assertEquals(vr2, latestCompletedForTrustAnchor.get());
        });

    }
}