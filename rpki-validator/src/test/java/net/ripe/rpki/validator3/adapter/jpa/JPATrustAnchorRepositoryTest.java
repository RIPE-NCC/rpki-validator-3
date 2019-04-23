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
package net.ripe.rpki.validator3.adapter.jpa;

import lombok.extern.slf4j.Slf4j;

import net.ripe.rpki.commons.validation.ValidationResult;
import net.ripe.rpki.validator3.IntegrationTest;
import net.ripe.rpki.validator3.TestObjects;
import net.ripe.rpki.validator3.api.trustanchors.TaStatus;
import net.ripe.rpki.validator3.domain.CertificateTreeValidationRun;
import net.ripe.rpki.validator3.domain.ErrorCodes;
import net.ripe.rpki.validator3.domain.RpkiObject;
import net.ripe.rpki.validator3.domain.TrustAnchor;
import net.ripe.rpki.validator3.domain.TrustAnchors;
import net.ripe.rpki.validator3.domain.ValidationCheck;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.ObjectRetrievalFailureException;
import org.springframework.test.context.junit4.SpringRunner;

import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import java.util.List;

import static net.ripe.rpki.validator3.domain.TrustAnchorsFactory.RIPE_NCC_TA_CERTIFICATE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

@RunWith(SpringRunner.class)
@IntegrationTest
@Transactional
@Slf4j
public class JPATrustAnchorRepositoryTest {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TrustAnchors subject;

    @Before
    public void clear(){
        subject.findAll().forEach(ta -> subject.remove(ta));
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    public void should_use_spring_data_access_Exceptions() {
        log.info("subject {}", subject.getClass());
        assertThatExceptionOfType(ObjectRetrievalFailureException.class).isThrownBy(() -> subject.get(-4));
    }

    @Test
    public void should_find_trust_anchors_by_case_insensitive_name() {
        TrustAnchor trustAnchor = TestObjects.newTrustAnchor();
        entityManager.persist(trustAnchor);

        List<TrustAnchor> byName = subject.findByName("Trust Anchor");
        assertThat(byName).isNotEmpty();
        assertThat(byName.get(0)).isEqualTo(trustAnchor);
    }

    @Test
    public void should_get_statuses() {

        TrustAnchor trustAnchor = TestObjects.newTrustAnchor();
        entityManager.persist(trustAnchor);

        CertificateTreeValidationRun validationRun = new CertificateTreeValidationRun(trustAnchor);
        String trustAnchorLocation = trustAnchor.getLocations().get(0);

        ValidationResult validationResult = ValidationResult.withLocation(trustAnchorLocation);

        RpkiObject validObject = new RpkiObject(trustAnchorLocation, RIPE_NCC_TA_CERTIFICATE);
        entityManager.persist(validObject);
        validationRun.getValidatedObjects().add(validObject);

        ValidationCheck some_weird_error = new ValidationCheck(validationRun, trustAnchorLocation, ValidationCheck.Status.ERROR, ErrorCodes.UNHANDLED_EXCEPTION, "Some weird error");
        validationRun.addCheck(some_weird_error);
        entityManager.persist(some_weird_error);

        ValidationCheck some_weird_warning  = new ValidationCheck(validationRun, trustAnchorLocation, ValidationCheck.Status.WARNING, ErrorCodes.UNHANDLED_EXCEPTION, "Some weird warning");
        validationRun.addCheck(some_weird_warning);
        entityManager.persist(some_weird_warning);

        validationRun.addChecks(validationResult);
        validationRun.completeWith(validationResult);
        entityManager.persist(validationRun);

        List<TaStatus> statuses = subject.getStatuses();

        assertThat(statuses.get(0).getTaName()).isEqualTo("trust anchor");
        assertThat(statuses.get(0).getErrors()).isEqualTo(1);
        assertThat(statuses.get(0).getWarnings()).isEqualTo(1);
        assertThat(statuses.get(0).getSuccessful()).isEqualTo(1);

    }
}
