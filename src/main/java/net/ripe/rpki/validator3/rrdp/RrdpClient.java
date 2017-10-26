package net.ripe.rpki.validator3.rrdp;

import java.io.InputStream;
import java.util.function.Function;

public interface RrdpClient {
    <T> T readStream(String uri, Function<InputStream, T> reader);
}
