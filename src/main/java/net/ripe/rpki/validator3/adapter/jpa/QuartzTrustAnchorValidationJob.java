package net.ripe.rpki.validator3.adapter.jpa;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.validator3.domain.validation.ValidationService;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;

@Slf4j
public class QuartzTrustAnchorValidationJob implements Job {

    public static final String TRUST_ANCHOR_ID_KEY = "trustAnchorId";

    @Autowired
    private ValidationService validationService;

    @Getter
    @Setter
    private long trustAnchorId;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        log.info("starting {} for trust anchor {}", getClass().getSimpleName(), trustAnchorId);

        validationService.validate(trustAnchorId);
    }
}
