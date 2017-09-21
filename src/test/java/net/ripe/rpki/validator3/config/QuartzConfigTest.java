package net.ripe.rpki.validator3.config;

import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.quartz.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.assertThat;

@RunWith(SpringRunner.class)
@SpringBootTest
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
