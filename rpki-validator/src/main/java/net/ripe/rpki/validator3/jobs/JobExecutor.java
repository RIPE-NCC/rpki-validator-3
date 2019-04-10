package net.ripe.rpki.validator3.jobs;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Value;
import net.ripe.rpki.validator3.domain.validation.CertificateTreeValidationService;
import net.ripe.rpki.validator3.domain.validation.RpkiRepositoryValidationService;
import net.ripe.rpki.validator3.domain.validation.TrustAnchorValidationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

@Component
public class JobExecutor {

    public Job repeat(Job job, Duration duration) {
        return new Job(
                job.queueId,
                job.type,
                () -> {
                    try {
                        job.r.run();
                    } finally {
                        submit(repeat(job, duration));
                    }
                });
    }

    public Job rrdpRepoValidation(long repositoryId) {
        return new Job(
                QueueId.of("rrdp:" + repositoryId),
                Type.RRDP_VALIDATION,
                () -> rpkiRepositoryValidationService.validateRpkiRepository(repositoryId));
    }

    private enum Type {
        TA_VALIDATION, CAT_VALIDATION, RRDP_VALIDATION, COMPOSITE
    }

    private final TrustAnchorValidationService trustAnchorValidationService;
    private final CertificateTreeValidationService certificateTreeValidationService;
    private final RpkiRepositoryValidationService rpkiRepositoryValidationService;

    @Autowired
    public JobExecutor(TrustAnchorValidationService trustAnchorValidationService,
                       CertificateTreeValidationService certificateTreeValidationService,
                       RpkiRepositoryValidationService rpkiRepositoryValidationService) {
        this.trustAnchorValidationService = trustAnchorValidationService;
        this.certificateTreeValidationService = certificateTreeValidationService;
        this.rpkiRepositoryValidationService = rpkiRepositoryValidationService;
    }

    private final Map<QueueId, List<Job>> jobs = new HashMap<>();


    private final ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

    public Job taValidation(long trustAnchorId) {
        return new Job(
                QueueId.of("trustAnchor:" + trustAnchorId),
                Type.TA_VALIDATION,
                () -> trustAnchorValidationService.validate(trustAnchorId));
    }

    public Job certificateTreeValidation(long trustAnchorId) {
        return new Job(
                QueueId.of("trustAnchor:" + trustAnchorId),
                Type.CAT_VALIDATION,
                () -> certificateTreeValidationService.validate(trustAnchorId));
    }

    public void submit(Job job) {
        synchronized (this.jobs) {
            List<Job> queue = this.jobs.get(job.getQueueId());
            if (queue == null) {
                queue = new ArrayList<>();
                queue.add(job);
                this.jobs.put(job.getQueueId(), queue);
            } else if (queue.stream().noneMatch(j -> j.getType() == job.getType())) {
                queue.add(job);
                this.jobs.put(job.getQueueId(), queue);
            }
            executor.submit(() -> {
                synchronized (this.jobs) {

                }
            });
        }
    }

    public void sequence(Job... newJobs) {
        synchronized (this.jobs) {
            Stream.of(newJobs).forEach(this::submit);
        }
    }

    @Value(staticConstructor = "of")
    private static class QueueId {
        private String key;
    }

    @Value
    @AllArgsConstructor
    @EqualsAndHashCode(exclude = "r")
    public static class Job {
        private QueueId queueId;
        private Type type;
        private Runnable r;
    }

    @AllArgsConstructor
    @EqualsAndHashCode
    static class JobWrap {
        @Getter
        private Job job;
    }

    @EqualsAndHashCode
    public static class Just extends JobWrap {
        public Just(Job job) {
            super(job);
        }
    }

    @EqualsAndHashCode
    public static class Repeat extends JobWrap {
        private final Duration interval;

        public Repeat(Job job, Duration interval) {
            super(job);
            this.interval = interval;
        }
    }
}
