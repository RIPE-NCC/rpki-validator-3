package net.ripe.rpki.validator3.jobs;

import net.ripe.rpki.validator3.IntegrationTest;
import net.ripe.rpki.validator3.storage.stores.impl.GenericStorageTest;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit4.SpringRunner;

import java.time.Duration;

@RunWith(SpringRunner.class)
@IntegrationTest
public class JobExecutorTest extends GenericStorageTest {

    @Autowired
    private JobExecutor exec;

    @Test
    public void testSmt() {

        // initial
        exec.sequence(
                exec.repeat(exec.taValidation(10L), Duration.ofMinutes(10)),
                exec.certificateTreeValidation(10L)
        );

        // loop
        exec.sequence(
//                exec.rrdpRepoValidation(20L) -> exec.submit(affectedTas.forEach(ta -> exec.taValidation(ta.id())))
        );


        exec.submit(exec.rrdpRepoValidation(20L));

        JobExecutor.Job job = exec.taValidation(10L);
    }

}