package net.ripe.rpki.validator3.adapter.jpa;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.validator3.domain.CertificateTreeValidationRun;
import net.ripe.rpki.validator3.domain.TrustAnchor;
import net.ripe.rpki.validator3.domain.validation.CertificateTreeValidationService;
import org.quartz.*;
import org.springframework.beans.factory.annotation.Autowired;

@DisallowConcurrentExecution
@Slf4j
public class QuartzCertificateTreeValidationJob implements Job {

    public static final String TRUST_ANCHOR_ID_KEY = "trustAnchorId";

    @Autowired
    private CertificateTreeValidationService validationService;

    @Getter
    @Setter
    private long trustAnchorId;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        log.info("starting {} for trust anchor {}", getClass().getSimpleName(), trustAnchorId);

        validationService.validate(trustAnchorId);
    }

    static JobDetail buildJob(TrustAnchor trustAnchor) {
        return JobBuilder.newJob(QuartzCertificateTreeValidationJob.class)
            .withIdentity(getJobKey(trustAnchor))
            .usingJobData(TRUST_ANCHOR_ID_KEY, trustAnchor.getId())
            .build();
    }

    static JobKey getJobKey(TrustAnchor trustAnchor) {
        return new JobKey(String.format("%s#%d", CertificateTreeValidationRun.TYPE, trustAnchor.getId()));
    }
}
