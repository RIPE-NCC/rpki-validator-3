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
package net.ripe.rpki.validator3.background;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.validator3.api.bgp.BgpPreviewService;
import net.ripe.rpki.validator3.domain.cleanup.RpkiObjectCleanupService;
import net.ripe.rpki.validator3.domain.cleanup.RpkiRepositoryCleanupService;
import net.ripe.rpki.validator3.domain.cleanup.ValidationRunCleanupService;
import net.ripe.rpki.validator3.domain.validation.RpkiRepositoryValidationService;
import net.ripe.rpki.validator3.storage.data.RpkiRepository;
import net.ripe.rpki.validator3.storage.data.TrustAnchor;
import net.ripe.rpki.validator3.storage.data.validation.TrustAnchorValidationRun;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Profile("!test")
@Component
@Slf4j
public class BackgroundJobs {

    private final ScheduledExecutorService scheduledExecutorService;

    private final RpkiObjectCleanupService rpkiObjectCleanupService;
    private final RpkiRepositoryCleanupService rpkiRepositoryCleanupService;
    private final ValidationRunCleanupService validationRunCleanupService;
    private final RpkiRepositoryValidationService rpkiRepositoryValidationService;
    private final BgpPreviewService bgpPreviewService;

    private final Map<String, Pair<Job, ScheduledFuture<?>>> jobs;

    @Autowired
    public BackgroundJobs(RpkiObjectCleanupService rpkiObjectCleanupService,
                          RpkiRepositoryCleanupService rpkiRepositoryCleanupService,
                          ValidationRunCleanupService validationRunCleanupService,
                          RpkiRepositoryValidationService rpkiRepositoryValidationService,
                          BgpPreviewService bgpPreviewService) {

        this.jobs = new ConcurrentHashMap<>();
        this.scheduledExecutorService = Executors.newScheduledThreadPool(32);

        this.rpkiObjectCleanupService = rpkiObjectCleanupService;
        this.rpkiRepositoryCleanupService = rpkiRepositoryCleanupService;
        this.validationRunCleanupService = validationRunCleanupService;
        this.rpkiRepositoryValidationService = rpkiRepositoryValidationService;
        this.bgpPreviewService = bgpPreviewService;
    }

    @PostConstruct
    public void initBackgroundTasks() {
        schedule(
            Job.of("rpkiObjectCleanupService", rpkiObjectCleanupService::cleanupRpkiObjects),
            3, 10, TimeUnit.MINUTES);

        schedule(
            Job.of("rpkiRepositoryCleanupService", rpkiRepositoryCleanupService::cleanupRpkiRepositories),
            4, 60, TimeUnit.MINUTES);

        schedule(
            Job.of("validationRunCleanupService", validationRunCleanupService::cleanupValidationRuns),
            5, 5, TimeUnit.MINUTES);

        schedule(
            Job.of("rpkiRepositoryValidationService", rpkiRepositoryValidationService::validateRsyncRepositories),
            11, 60, TimeUnit.SECONDS);

        schedule(
            Job.of("bgpPreviewService", bgpPreviewService::downloadRisPreview),
            7, 600, TimeUnit.SECONDS);
    }

    public void schedule(Job job, long delay, long period, TimeUnit timeUnit) {
        final Runnable rr = () -> {
            jobToBeExecuted(job.getKey());
            try {
                job.getRunnable().run();
            } catch (Exception e) {
                log.error(String.format("Error executing job '%s'", job.getKey()), e);
            } finally {
                jobWasExecuted(job.getKey());
            }
        };
        final ScheduledFuture<?> scheduledFuture = scheduledExecutorService.scheduleAtFixedRate(rr, delay, period, timeUnit);
        jobs.put(job.getKey(), Pair.of(job, scheduledFuture));

        log.info(String.format("Scheduled '%s'", job.getKey()));
    }

    public boolean jobExists(String key) {
        return jobs.containsKey(key);
    }

    public boolean deleteJob(String key) {
        final Pair<Job, ScheduledFuture<?>> pair = jobs.remove(key);
        if (pair != null) {
            pair.getRight().cancel(false);
            return true;
        }
        return false;
    }

    @Data(staticConstructor = "of")
    public static class Execution {
        public final Instant lastStarted;
        public final Instant lastFinished;
        public final long count;
        public final long totalRunTime;
        public final long averageRunTime;

        static Execution again(Execution e) {
            if (e == null) {
                return of(now(), null, 1, 0, 0);
            }
            return of(now(), null, e.count + 1, e.totalRunTime, e.totalRunTime / (e.count + 1));
        }

        static Execution finish(Execution e) {
            final Instant finished = now();
            final long newTotalTime = e.totalRunTime + Duration.between(e.lastStarted, finished).toMillis();
            return of(e.lastStarted, finished, e.count, newTotalTime, newTotalTime / e.count);
        }

        static Instant now() {
            return Instant.now(Clock.systemUTC());
        }
    }

    private final TreeMap<String, Execution> backgroundJobStats = new TreeMap<>();

    public void jobToBeExecuted(String key) {
        synchronized (backgroundJobStats) {
            final Execution execution = backgroundJobStats.get(key);
            backgroundJobStats.put(key, Execution.again(execution));
        }
    }

    public void jobWasExecuted(String key) {
        synchronized (backgroundJobStats) {
            final Execution execution = backgroundJobStats.get(key);
            if (execution != null) {
                backgroundJobStats.put(key, Execution.finish(execution));
            }
        }
    }

    public static String getTaValidationJobKey(TrustAnchor trustAnchor) {
        return String.format("%s#%s#%d", TrustAnchorValidationRun.TYPE,
            trustAnchor.getName(), trustAnchor.key().asLong());
    }

    public static String getRrdpRepoValidationJobKey(RpkiRepository rpkiRepository) {
        return String.format("%s#%s#%d", rpkiRepository.getType(),
            rpkiRepository.getRrdpNotifyUri(), rpkiRepository.key().asLong());
    }

    public SortedMap<String, Execution> getStat() {
        synchronized (backgroundJobStats) {
            return backgroundJobStats;
        }
    }
}
