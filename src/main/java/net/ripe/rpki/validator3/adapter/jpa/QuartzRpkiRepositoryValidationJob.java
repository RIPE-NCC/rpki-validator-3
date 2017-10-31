package net.ripe.rpki.validator3.adapter.jpa;

import lombok.Getter;
import lombok.Setter;
import net.ripe.rpki.validator3.domain.validation.RpkiRepositoryValidationService;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;

@DisallowConcurrentExecution
public class QuartzRpkiRepositoryValidationJob implements Job {

    public static final String RPKI_REPOSITORY_ID = "rpkiRepositoryId";

    @Autowired
    private RpkiRepositoryValidationService validationService;

    @Getter
    @Setter
    private long rpkiRepositoryId;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        validationService.validateRpkiRepository(rpkiRepositoryId);
    }
}
