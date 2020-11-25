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
import org.quartz.listeners.JobListenerSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Profile("!test")
@Component
@Slf4j
public class BackgroundJobs extends JobListenerSupport {

    private final ScheduledExecutorService scheduledExecutorService;

    private final RpkiObjectCleanupService rpkiObjectCleanupService;
    private final RpkiRepositoryCleanupService rpkiRepositoryCleanupService;
    private final ValidationRunCleanupService validationRunCleanupService;
    private final RpkiRepositoryValidationService rpkiRepositoryValidationService;
    private final BgpPreviewService bgpPreviewService;

    @Autowired
    public BackgroundJobs(RpkiObjectCleanupService rpkiObjectCleanupService,
                          RpkiRepositoryCleanupService rpkiRepositoryCleanupService,
                          ValidationRunCleanupService validationRunCleanupService,
                          RpkiRepositoryValidationService rpkiRepositoryValidationService,
                          BgpPreviewService bgpPreviewService) {
        this.scheduledExecutorService = Executors.newScheduledThreadPool(Runtime.getRuntime().availableProcessors());

        this.rpkiObjectCleanupService = rpkiObjectCleanupService;
        this.rpkiRepositoryCleanupService = rpkiRepositoryCleanupService;
        this.validationRunCleanupService = validationRunCleanupService;
        this.rpkiRepositoryValidationService = rpkiRepositoryValidationService;
        this.bgpPreviewService = bgpPreviewService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initBackgroundTasks() {
        schedule("rpkiObjectCleanupService", 3, 10, TimeUnit.MINUTES,
            () -> rpkiObjectCleanupService.cleanupRpkiObjects());

        schedule("rpkiRepositoryCleanupService", 4, 60, TimeUnit.MINUTES,
            () -> rpkiRepositoryCleanupService.cleanupRpkiRepositories());

        schedule("validationRunCleanupService", 5, 5, TimeUnit.MINUTES,
            () -> validationRunCleanupService.cleanupValidationRuns());

        schedule("rpkiRepositoryValidationService", 10, 60, TimeUnit.SECONDS,
            () -> rpkiRepositoryValidationService.validateRsyncRepositories());

        schedule("bgpPreviewService", 10, 600, TimeUnit.SECONDS,
            () -> bgpPreviewService.downloadRisPreview());
    }

    private void schedule(String key, long delay, long period, TimeUnit timeUnit, Runnable r) {
        final Runnable rr = () -> {
            jobToBeExecuted(key);
            try {
                r.run();
            } catch (Exception e) {
                log.error(String.format("Error executing job '%s'", key), e);
            } finally {
                jobWasExecuted(key);
            }
        };
        scheduledExecutorService.scheduleAtFixedRate(rr, delay, period, timeUnit);
        log.info(String.format("Scheduled '%s'", key));
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
            return of(now(), null, e.count + 1, e.totalRunTime, e.totalRunTime/(e.count + 1));
        }

        static Execution finish(Execution e) {
            final Instant finished = now();
            final long newTotalTime = e.totalRunTime + Duration.between(e.lastStarted, finished).toMillis();
            return of(e.lastStarted, finished, e.count, newTotalTime, newTotalTime/e.count);
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

    @Override
    public String getName() {
        return "Background jobs stat collector";
    }

    public SortedMap<String, Execution> getStat() {
        synchronized (backgroundJobStats) {
            return backgroundJobStats;
        }
    }
}
