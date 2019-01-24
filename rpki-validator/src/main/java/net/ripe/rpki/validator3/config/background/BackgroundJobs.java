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
package net.ripe.rpki.validator3.config.background;

import lombok.extern.slf4j.Slf4j;
import org.quartz.Job;
import org.quartz.JobKey;
import org.quartz.ScheduleBuilder;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Date;

import static org.quartz.DateBuilder.IntervalUnit.MINUTE;
import static org.quartz.DateBuilder.IntervalUnit.SECOND;
import static org.quartz.DateBuilder.futureDate;
import static org.quartz.JobBuilder.newJob;
import static org.quartz.SimpleScheduleBuilder.simpleSchedule;
import static org.quartz.TriggerBuilder.newTrigger;

@Component
@Slf4j
public class BackgroundJobs {

    private final Scheduler scheduler;

    @Autowired
    public BackgroundJobs(Scheduler scheduler,
                          @Value("${rpki.validator.validation.run.cleanup.interval.ms:3600000}") int validationRunCleanUpDelay,
                          @Value("${rpki.validator.rpki.object.cleanup.interval.ms:3600000}") int rpkiObjectsRunCleanUpDelay,
                          BackgroundJobInfo listener
                          ) throws SchedulerException {

        this.scheduler = scheduler;

        scheduler.getListenerManager()
                .addJobListener(listener);

        schedule(RpkiObjectCleanupJob.class,
                futureDate(1, MINUTE),
                simpleSchedule().repeatForever().withIntervalInMilliseconds(rpkiObjectsRunCleanUpDelay));

        schedule(ValidationRunCleanupJob.class,
                futureDate(2, MINUTE),
                simpleSchedule().repeatForever().withIntervalInMilliseconds(validationRunCleanUpDelay));

        schedule(ValidateRsyncRepositoriesJob.class,
                futureDate(10, SECOND),
                simpleSchedule().repeatForever().withIntervalInSeconds(10));

        schedule(DownloadBgpRisDumpsJob.class,
                futureDate(10, SECOND),
                simpleSchedule().repeatForever().withIntervalInMinutes(10));

        schedule(H2CheckpointJob.class,
                futureDate(10, SECOND),
                simpleSchedule().repeatForever().withIntervalInMinutes(1));
    }

    private <T extends Trigger> void schedule(Class<? extends Job> jobClass, Date startAt, ScheduleBuilder<T> schedule) throws SchedulerException {
        final JobKey jobKey = JobKey.jobKey(jobClass.getName());
        if (scheduler.checkExists(jobKey)) {
            scheduler.deleteJob(jobKey);
        }
        scheduler.scheduleJob(
                newJob(jobClass)
                        .withIdentity(jobKey)
                        .storeDurably(false)
                        .build(),
                newTrigger()
                        .withIdentity(jobKey + "-Trigger")
                        .startAt(startAt)
                        .withSchedule(schedule)
                        .build()
        );
        log.info(String.format("Scheduled '%s', starting from '%s'", jobKey, startAt));
    }
}
