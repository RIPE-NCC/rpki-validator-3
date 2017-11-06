package net.ripe.rpki.validator3.rrdp;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public class RrdpClientStub implements RrdpClient {

    private Map<String, byte[]> contents = new HashMap<>();

    @Override
    public <T> T readStream(String uri, Function<InputStream, T> reader) {
        final byte[] bytes = contents.get(uri);
        return reader.apply(new ByteArrayInputStream(bytes));
    }

    @Override
    public byte[] getBody(String uri) {
        return contents.get(uri);
    }

    public void add(String uri, byte[] content) {
        contents.put(uri, content);
    }
}
