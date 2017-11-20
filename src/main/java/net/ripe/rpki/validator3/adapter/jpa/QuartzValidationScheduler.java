package net.ripe.rpki.validator3.adapter.jpa;

import com.google.common.base.Preconditions;
import net.ripe.rpki.validator3.api.Api;
import net.ripe.rpki.validator3.domain.RpkiRepository;
import net.ripe.rpki.validator3.domain.TrustAnchor;
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
