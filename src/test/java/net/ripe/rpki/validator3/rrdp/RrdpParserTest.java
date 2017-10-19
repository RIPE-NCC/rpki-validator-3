package net.ripe.rpki.validator3.rrdp;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class RrdpParserTest {

    @Test
    public void should_parse_snapshot() throws Exception {
        final Snapshot snapshot = new RrdpParser().snapshot(fileIS("rrdp/snapshot1.xml"));
        assertNotNull(snapshot.asMap().get("rsync://bandito.ripe.net/repo/671570f06499fbd2d6ab76c4f22566fe49d5de60.cer"));
        assertNotNull(snapshot);
    }

    @Test
    public void should_parse_notification() throws Exception {
        final Notification notification = new RrdpParser().notification(fileIS("rrdp/notification1.xml"));
        assertEquals("9df4b597-af9e-4dca-bdda-719cce2c4e28", notification.sessionId);
        assertEquals("http://repo.net/repo/snapshot.xml", notification.snapshotUri);
        assertEquals("EEEA7F7AD96D85BBD1F7274FA7DA0025984A2AF3D5A0538F77BEC732ECB1B068", notification.snapshotHash);
        assertEquals(BigInteger.ONE, notification.serial);
        assertEquals(0, notification.deltas.size());
    }

    private static String file(String path) throws IOException {
        final InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(path);
        try (BufferedReader buffer = new BufferedReader(new InputStreamReader(is))) {
            return buffer.lines().collect(Collectors.joining("\n"));
        }
    }

    private static InputStream fileIS(String path) throws IOException {
        return Thread.currentThread().getContextClassLoader().getResourceAsStream(path);
    }

}