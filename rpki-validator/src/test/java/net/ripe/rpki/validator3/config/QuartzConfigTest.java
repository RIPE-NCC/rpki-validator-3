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
package net.ripe.rpki.validator3.config;

import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.validator3.IntegrationTest;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.quartz.Job;
import org.quartz.JobBuilder;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.TriggerBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(SpringRunner.class)
@IntegrationTest
@Slf4j
public class QuartzConfigTest {

    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private Scheduler scheduler;

    private static CountDownLatch triggeredByTestJob;

    @Before
    public void setUp() throws SchedulerException {
        triggeredByTestJob = new CountDownLatch(1);
    }

    @Test
    public void quartz_job_should_not_run_on_transaction_rollback() throws InterruptedException {
        transactionTemplate.execute((status) -> {
            try {
                scheduler.scheduleJob(
                    JobBuilder.newJob(TestJob.class).build(),
                    TriggerBuilder.newTrigger().build()
                );
                scheduler.start();

                boolean completed = triggeredByTestJob.await(1, TimeUnit.SECONDS);
                assertThat(completed).describedAs("test job triggered before transaction completed").isFalse();

                status.setRollbackOnly();

                return null;
            } catch (SchedulerException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
        log.info("Transaction rolled back");

        boolean completed = triggeredByTestJob.await(1, TimeUnit.SECONDS);
        assertThat(completed).describedAs("test job triggered after transaction rollback").isFalse();
    }

    @Test
    public void quartz_job_should_not_start_before_transaction_completes() throws InterruptedException, SchedulerException {
        log.info("Starting transaction");
        transactionTemplate.execute((status) -> {
            try {
                scheduler.scheduleJob(
                    JobBuilder.newJob(TestJob.class).build(),
                    TriggerBuilder.newTrigger().build()
                );
                scheduler.start();

                boolean completed = triggeredByTestJob.await(1, TimeUnit.SECONDS);
                assertThat(completed).describedAs("test job triggered before transaction completion").isFalse();
            } catch (SchedulerException | InterruptedException e) {
                throw new RuntimeException(e);
            }
            return null;
        });

        log.info("Transaction committed");

        scheduler.start();

        boolean completed = triggeredByTestJob.await(1, TimeUnit.SECONDS);
        assertThat(completed).describedAs("test job triggered after commit").isTrue();
        log.info("Test completed");
    }


    @Slf4j
    private static class TestJob implements Job {
        @Override
        public void execute(JobExecutionContext context) throws JobExecutionException {
            log.info("triggering count down latch");
            triggeredByTestJob.countDown();
        }
    }
}
