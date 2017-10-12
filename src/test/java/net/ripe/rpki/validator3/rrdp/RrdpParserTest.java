package net.ripe.rpki.validator3.rrdp;

import org.eclipse.jetty.util.StringUtil;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

public class RrdpParserTest {

    @Test
    public void snapshot() throws Exception {
        final Snapshot snapshot = new RrdpParser().snapshot(file("rrdp/snapshot1.xml"));
        assertNotNull(snapshot);
    }

    private static String file(String path) throws IOException {
        final InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(path);
        try (BufferedReader buffer = new BufferedReader(new InputStreamReader(is))) {
            return buffer.lines().collect(Collectors.joining("\n"));
        }
    }

}