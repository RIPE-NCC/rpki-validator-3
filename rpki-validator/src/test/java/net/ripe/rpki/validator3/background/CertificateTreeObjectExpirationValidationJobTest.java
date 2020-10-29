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
package net.ripe.rpki.validator3.background;

import net.ripe.rpki.commons.validation.ValidationResult;
import net.ripe.rpki.validator3.api.util.InstantWithoutNanos;
import net.ripe.rpki.validator3.domain.ta.TrustAnchorsFactory;
import net.ripe.rpki.validator3.storage.data.Ref;
import net.ripe.rpki.validator3.storage.data.TrustAnchor;
import net.ripe.rpki.validator3.storage.data.validation.CertificateTreeValidationRun;
import net.ripe.rpki.validator3.storage.stores.impl.GenericStorageTest;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;

@RunWith(SpringRunner.class)
@ActiveProfiles(profiles="test")
@SpringBootTest(properties = "rpki.validator.strict-validation=true")
public class CertificateTreeObjectExpirationValidationJobTest extends GenericStorageTest {
    @Autowired
    private TrustAnchorsFactory factory;

    @Autowired
    private AutowireCapableBeanFactory beanFactory;
    private CertificateTreeObjectExpirationValidationJob subject;
    private TrustAnchor trustAnchor;
    private Ref<TrustAnchor> trustAnchorRef;
    private TrustAnchor validationTriggered;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        getValidationScheduler().disable();

        trustAnchor = factory.createRipeNccTrustAnchor();
        trustAnchorRef = wtx(tx -> {
            getTrustAnchors().add(tx, trustAnchor);
            return getTrustAnchors().makeRef(tx, trustAnchor.key());
        });

        subject = new CertificateTreeObjectExpirationValidationJob();
        beanFactory.autowireBean(subject);
        subject.triggerCertificateTreeValidation = (ta) -> this.validationTriggered = ta;
    }

    @Test
    public void trigger_execution_when_last_succeeded_validation_run_validation_neeeded_at_is_in_the_past() throws JobExecutionException {
        CertificateTreeValidationRun run = new CertificateTreeValidationRun(trustAnchorRef);
        run.setEarliestObjectExpiration(InstantWithoutNanos.now());
        run.completeWith(ValidationResult.withLocation("unused-location"));
        wtx0(tx -> this.getValidationRuns().add(tx, run));

        subject.execute(null);

        assertEquals(trustAnchor, validationTriggered);
    }

    @Test
    public void skip_execution_when_validation_run_is_still_running() throws JobExecutionException {
        CertificateTreeValidationRun run = new CertificateTreeValidationRun(trustAnchorRef);
        run.setEarliestObjectExpiration(InstantWithoutNanos.now());
        wtx0(tx -> this.getValidationRuns().add(tx, run));

        subject.execute(null);

        assertNull(validationTriggered);
    }

    @Test
    public void skip_execution_when_last_succeeded_validation_run_validation_neeeded_at_is_in_the_future() throws JobExecutionException {
        CertificateTreeValidationRun run = new CertificateTreeValidationRun(trustAnchorRef);
        run.setEarliestObjectExpiration(InstantWithoutNanos.from(Instant.now().plus(10, ChronoUnit.HOURS)));
        wtx0(tx -> this.getValidationRuns().add(tx, run));

        subject.execute(null);

        assertNull(validationTriggered);
    }

    @Test
    public void skip_execution_when_there_is_no_validation_run() throws JobExecutionException {
        subject.execute(null);

        assertNull(validationTriggered);
    }
}