package net.ripe.rpki.validator3.rrdp;

import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest
@Ignore
public class RrdpIntegrationTest {

    @Autowired
    private RrdpService subject;

    @Test
    public void should_parse_snapshot() throws Exception {
        subject.storeRepository("https://rrdp.ripe.net/notification.xml");
    }

}