package net.ripe.rpki.validator3.util;

import java.io.File;
import java.io.IOException;
import java.net.URI;

public class RsyncUtils {
    public static final int DEFAULT_RSYNC_PORT = 873;

    private RsyncUtils() {
    }

    public static File localFileFromRsyncUri(File localRsyncDirectory, URI rsyncUri) throws IOException {
        URI location = rsyncUri.normalize();
        String host = location.getHost() + "/" + (location.getPort() < 0 ? DEFAULT_RSYNC_PORT : location.getPort());
        return new File(
            new File(localRsyncDirectory, host),
            location.getRawPath()
        ).getCanonicalFile();

    }
}
