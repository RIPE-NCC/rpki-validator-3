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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

@Component
public class JobExecutor {

    private final ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(1);
    private final ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

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


    public Job taValidation(long trustAnchorId) {
        return new Job(
                idSeq.getAndIncrement(),
                QueueId.of("trustAnchor:" + trustAnchorId),
                Type.TA_VALIDATION,
                () -> trustAnchorValidationService.validate(trustAnchorId));
    }

    public Job certificateTreeValidation(long trustAnchorId) {
        return new Job(
                idSeq.getAndIncrement(),
                QueueId.of("trustAnchor:" + trustAnchorId),
                Type.CAT_VALIDATION,
                () -> certificateTreeValidationService.validate(trustAnchorId));
    }

    public Job rrdpRepoValidation(long repositoryId) {
        return new Job(
                idSeq.getAndIncrement(),
                QueueId.of("rrdp:" + repositoryId),
                Type.RRDP_VALIDATION,
                () -> rpkiRepositoryValidationService.validateRrdpRpkiRepository(repositoryId));
    }


    private AtomicLong idSeq = new AtomicLong(1);

    public void repeat(Job job, Duration duration) {
        scheduledExecutorService.scheduleAtFixedRate(
                () -> this.submit(job), 0, duration.toMillis(), TimeUnit.MILLISECONDS);
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
                    jobs.values().stream().findFirst().ifPresent(q -> {
                        q.stream().findFirst().ifPresent(j -> {

                        });
                    });

//                    currentlyExecuted.add()
                }
            });
        }
    }

    private final Set<Long> currentlyExecuted = new HashSet<>();


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
        private long id;
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

    @EqualsAndHashCode(callSuper = true)
    public static class Just extends JobWrap {
        public Just(Job job) {
            super(job);
        }
    }

    @EqualsAndHashCode(callSuper = true)
    public static class Repeat extends JobWrap {
        private final Duration interval;

        public Repeat(Job job, Duration interval) {
            super(job);
            this.interval = interval;
        }
    }
}
