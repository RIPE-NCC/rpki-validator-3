package net.ripe.rpki.validator3.util;

import net.ripe.rpki.validator3.IntegrationTest;
import net.ripe.rpki.validator3.rrdp.Notification;
import net.ripe.rpki.validator3.rrdp.RrdpParser;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.http.HttpHeader;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.UUID;

import static org.junit.Assert.assertNotNull;

@RunWith(SpringRunner.class)
@IntegrationTest
class HttpTest {
    @Autowired
    private Http http;
    private HttpClient client;
    private final RrdpParser rrdpParser = new RrdpParser();

    @Test()
    void fetchRipeRRDPWithHEB() throws Exception {
        client = http.client();
        client.start();

        Assertions.assertDoesNotThrow(() -> {
            Notification notification = Http.readStream(() -> {
                final Request request = client.newRequest("https://rrdp.ripe.net/notification.xml");
                request.header(HttpHeader.USER_AGENT, null);
                request.header(HttpHeader.USER_AGENT, UUID.randomUUID().toString());
                return request;
            }, rrdpParser::notification);
            assertNotNull(notification);
            assertNotNull(notification.serial);
        });
    }

    @Test()
    void fetchNLNetlabRRDPWithHEB() throws Exception {
        client = http.client();
        client.start();
        Assertions.assertDoesNotThrow(() -> {
            Notification notification = Http.readStream(() -> {
                final Request request = client.newRequest("https://rrdp.rpki.nlnetlabs.nl/rrdp/notification.xml");
                request.header(HttpHeader.USER_AGENT, null);
                request.header(HttpHeader.USER_AGENT, "RIPE NCC RPKI Validator/test");
                return request;
            }, rrdpParser::notification);
            assertNotNull(notification);
            assertNotNull(notification.serial);
        });
    }

}