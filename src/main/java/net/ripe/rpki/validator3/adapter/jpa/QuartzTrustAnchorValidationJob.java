package net.ripe.rpki.validator3.adapter.jpa;

import lombok.Getter;
import lombok.Setter;
import net.ripe.rpki.validator3.domain.TrustAnchor;
import net.ripe.rpki.validator3.domain.TrustAnchorValidationRun;
import net.ripe.rpki.validator3.domain.validation.ValidationService;
import org.quartz.*;
import org.springframework.beans.factory.annotation.Autowired;

@DisallowConcurrentExecution
public class QuartzTrustAnchorValidationJob implements Job {

    public static final String TRUST_ANCHOR_ID_KEY = "trustAnchorId";

    @Autowired
    private ValidationService validationService;

    @Getter
    @Setter
    private long trustAnchorId;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        validationService.validate(trustAnchorId);
    }

    static JobDetail buildJob(TrustAnchor trustAnchor) {
        return JobBuilder.newJob(QuartzTrustAnchorValidationJob.class)
            .withIdentity(getJobKey(trustAnchor))
            .usingJobData(TRUST_ANCHOR_ID_KEY, trustAnchor.getId())
            .build();
    }

    static JobKey getJobKey(TrustAnchor trustAnchor) {
        return new JobKey(String.format("%s#%d", TrustAnchorValidationRun.TYPE, trustAnchor.getId()));
    }
}
