package net.ripe.rpki.validator3.domain.cleanup;

import net.ripe.rpki.validator3.IntegrationTest;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit4.SpringRunner;

import javax.transaction.Transactional;


@RunWith(SpringRunner.class)
@IntegrationTest
@Transactional
public class ValidationRunCleanupServiceTest {

    @Autowired
    private ValidationRunCleanupService subject;


    @Test
    public void run() {
        subject.cleanupValidationRuns();
    }
}
