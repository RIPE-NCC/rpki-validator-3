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

import com.google.common.base.CaseFormat;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.JobKey;
import org.quartz.ScheduleBuilder;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.listeners.JobListenerSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import static org.quartz.DateBuilder.IntervalUnit.MINUTE;
import static org.quartz.DateBuilder.IntervalUnit.SECOND;
import static org.quartz.DateBuilder.futureDate;
import static org.quartz.JobBuilder.newJob;
import static org.quartz.SimpleScheduleBuilder.simpleSchedule;
import static org.quartz.TriggerBuilder.newTrigger;

@Profile("!test")
@Component
@Slf4j
public class BackgroundJobs extends JobListenerSupport {

    private final Scheduler scheduler;

    @Autowired
    public BackgroundJobs(Scheduler scheduler) throws SchedulerException {

        this.scheduler = scheduler;

        scheduler.getListenerManager().addJobListener(this);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initBackgroundTasks() throws SchedulerException {
        schedule(RpkiObjectCleanupJob.class,
                futureDate(3, MINUTE),
                simpleSchedule().repeatForever().withIntervalInMinutes(10));

        schedule(RpkiRepositoryCleanupJob.class,
                futureDate(4, MINUTE),
                simpleSchedule().repeatForever().withIntervalInMinutes(60));

        schedule(ValidationRunCleanupJob.class,
                futureDate(5, MINUTE),
                simpleSchedule().repeatForever().withIntervalInMinutes(5));

        schedule(ValidateRsyncRepositoriesJob.class,
                futureDate(10, SECOND),
                simpleSchedule().repeatForever().withIntervalInMinutes(5));

        schedule(DownloadBgpRisDumpsJob.class,
                futureDate(10, SECOND),
                simpleSchedule().repeatForever().withIntervalInMinutes(10));
    }

    private <T extends Trigger> void schedule(Class<? extends Job> jobClass, Date startAt, ScheduleBuilder<T> schedule) throws SchedulerException {
        final JobKey jobKey = JobKey.jobKey(CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_HYPHEN, jobClass.getSimpleName()));
        if (scheduler.checkExists(jobKey)) {
            scheduler.deleteJob(jobKey);
        }
        scheduler.scheduleJob(
                newJob(jobClass)
                        .withIdentity(jobKey)
                        .build(),
                newTrigger()
                        .withIdentity(jobKey + "-Trigger")
                        .startAt(startAt)
                        .withSchedule(schedule)
                        .build()
        );
        log.info(String.format("Scheduled '%s', starting from '%s'", jobKey, startAt));
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

    @Override
    public void jobToBeExecuted(JobExecutionContext context) {
        final String key = context.getJobDetail().getKey().toString();
        synchronized (backgroundJobStats) {
            final Execution execution = backgroundJobStats.get(key);
            backgroundJobStats.put(key, Execution.again(execution));
        }
    }

    @Override
    public void jobWasExecuted(JobExecutionContext context, JobExecutionException jobException) {
        final String key = context.getJobDetail().getKey().toString();
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
