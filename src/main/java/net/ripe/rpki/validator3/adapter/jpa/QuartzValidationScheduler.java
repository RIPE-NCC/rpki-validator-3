package net.ripe.rpki.validator3.adapter.jpa;

import com.google.common.base.Preconditions;
import net.ripe.rpki.validator3.api.Api;
import net.ripe.rpki.validator3.domain.TrustAnchor;
import org.quartz.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.transaction.Transactional;

@Component
public class QuartzValidationScheduler {

    private final Scheduler scheduler;

    @Autowired
    public QuartzValidationScheduler(Scheduler scheduler) {
        this.scheduler = scheduler;
    }

    @Transactional(Transactional.TxType.MANDATORY)
    public void addTrustAnchor(TrustAnchor trustAnchor) {
        Preconditions.checkArgument(
            trustAnchor.getId() >= Api.MINIMUM_VALID_ID,
            "trustAnchor id %s is not valid",
            trustAnchor.getId()
        );

        try {
            scheduler.scheduleJob(
                JobBuilder.newJob(QuartzValidationJob.class)
                    .withIdentity(getJobKey(trustAnchor))
                    .usingJobData(QuartzValidationJob.TRUST_ANCHOR_ID_KEY, trustAnchor.getId())
                    .build(),
                TriggerBuilder.newTrigger()
                    .startNow()
                    .withSchedule(SimpleScheduleBuilder.repeatMinutelyForever(10))
                    .build()
            );
        } catch (SchedulerException ex) {
            throw new RuntimeException(ex);
        }
    }

    private JobKey getJobKey(TrustAnchor trustAnchor) {
        return new JobKey(String.format("validate-trust-anchor#%d", trustAnchor.getId()));
    }
}
