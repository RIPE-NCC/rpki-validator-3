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
package net.ripe.rpki.rtr.adapter.jpa;

import com.google.common.base.Preconditions;
import net.ripe.rpki.rtr.api.Api;
import net.ripe.rpki.rtr.domain.RpkiRepository;
import net.ripe.rpki.rtr.domain.TrustAnchor;
import org.quartz.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Component;

import javax.transaction.Transactional;

@Component
@Transactional(Transactional.TxType.MANDATORY)
public class QuartzValidationScheduler {

    private final Scheduler scheduler;

    @Autowired
    public QuartzValidationScheduler(Scheduler scheduler) {
        this.scheduler = scheduler;
    }

    public void addTrustAnchor(TrustAnchor trustAnchor) {
        Preconditions.checkArgument(
            trustAnchor.getId() >= Api.MINIMUM_VALID_ID,
            "trustAnchor id %s is not valid",
            trustAnchor.getId()
        );

        try {
            scheduler.scheduleJob(
                QuartzTrustAnchorValidationJob.buildJob(trustAnchor),
                TriggerBuilder.newTrigger()
                    .startNow()
                    .withSchedule(SimpleScheduleBuilder.repeatMinutelyForever(10))
                    .build()
            );
            scheduler.addJob(QuartzCertificateTreeValidationJob.buildJob(trustAnchor), true);
        } catch (SchedulerException ex) {
            throw new RuntimeException(ex);
        }
    }

    public void removeTrustAnchor(TrustAnchor trustAnchor) {
        try {
            boolean trustAnchorValidationDeleted = scheduler.deleteJob(QuartzTrustAnchorValidationJob.getJobKey(trustAnchor));
            boolean certificateTreeValidationDeleted = scheduler.deleteJob(QuartzCertificateTreeValidationJob.getJobKey(trustAnchor));
            if (!trustAnchorValidationDeleted || !certificateTreeValidationDeleted) {
                throw new EmptyResultDataAccessException("validation job for trust anchor or certificate tree not found", 1);
            }
        } catch (SchedulerException ex) {
            throw new RuntimeException(ex);
        }
    }

    public void addRpkiRepository(RpkiRepository rpkiRepository) {
        Preconditions.checkArgument(
            rpkiRepository.getId() >= Api.MINIMUM_VALID_ID,
            "rpkiRepository id %s is not valid",
            rpkiRepository.getId()
        );

        try {
            scheduler.scheduleJob(
                QuartzRpkiRepositoryValidationJob.buildJob(rpkiRepository),
                TriggerBuilder.newTrigger()
                    .startNow()
                    .withSchedule(SimpleScheduleBuilder.repeatMinutelyForever(1))
                    .build()
            );
        } catch (SchedulerException ex) {
            throw new RuntimeException(ex);
        }
    }

    public void removeRpkiRepository(RpkiRepository repository) {
        try {
            boolean jobDeleted = scheduler.deleteJob(QuartzRpkiRepositoryValidationJob.getJobKey(repository));
            if (!jobDeleted) {
                throw new EmptyResultDataAccessException("validation job for RPKI repository not found", 1);
            }
        } catch (SchedulerException ex) {
            throw new RuntimeException(ex);
        }
    }

    public void triggerCertificateTreeValidation(TrustAnchor trustAnchor) {
        try {
            scheduler.triggerJob(QuartzCertificateTreeValidationJob.getJobKey(trustAnchor));
        } catch (SchedulerException ex) {
            throw new RuntimeException(ex);
        }
    }
}
