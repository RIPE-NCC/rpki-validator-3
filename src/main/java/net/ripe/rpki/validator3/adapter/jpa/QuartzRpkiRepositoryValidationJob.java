package net.ripe.rpki.validator3.adapter.jpa;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.validator3.domain.validation.ValidationService;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;

@DisallowConcurrentExecution
@Slf4j
public class QuartzRpkiRepositoryValidationJob implements Job {

    public static final String RPKI_REPOSITORY_ID = "rpkiRepositoryId";

    @Autowired
    private ValidationService validationService;

    @Getter
    @Setter
    private long rpkiRepositoryId;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        log.info("starting {} for RPKI repository {}", getClass().getSimpleName(), rpkiRepositoryId);

        validationService.validateRpkiRepository(rpkiRepositoryId);
    }
}
