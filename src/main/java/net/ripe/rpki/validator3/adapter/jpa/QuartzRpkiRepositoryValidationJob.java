package net.ripe.rpki.validator3.adapter.jpa;

import lombok.Getter;
import lombok.Setter;
import net.ripe.rpki.validator3.domain.RpkiRepository;
import net.ripe.rpki.validator3.domain.RpkiRepositoryValidationRun;
import net.ripe.rpki.validator3.domain.validation.RpkiRepositoryValidationService;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.JobKey;
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

    static JobDetail buildJob(RpkiRepository rpkiRepository) {
        return JobBuilder.newJob(QuartzRpkiRepositoryValidationJob.class)
            .withIdentity(getJobKey(rpkiRepository))
            .usingJobData(RPKI_REPOSITORY_ID, rpkiRepository.getId())
            .build();
    }

    static JobKey getJobKey(RpkiRepository rpkiRepository) {
        return new JobKey(String.format("%s#%d", RpkiRepositoryValidationRun.TYPE, rpkiRepository.getId()));
    }
}
