package net.ripe.rpki.validator3.util;

import org.bouncycastle.crypto.Digest;
import org.bouncycastle.crypto.digests.SHA256Digest;

import java.io.*;
import java.util.Arrays;
import java.util.List;

public class Sha256 {
    public static byte[] hash(byte[] data) {
        try (ByteArrayInputStream in = new ByteArrayInputStream(data)) {
            return hash(in);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static byte[] hash(File targetFile) throws IOException {
        try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(targetFile))) {
            return hash(in);
        }
    }

    public static byte[] hash(InputStream in) throws IOException {
        Digest digest = new SHA256Digest();
        byte[] data = new byte[8192];

        int len;
        while ((len = in.read(data)) >= 0) {
            digest.update(data, 0, len);
        }

        byte[] result = new byte[digest.getDigestSize()];
        digest.doFinal(result, 0);

        return result;
    }

    public static byte[] parse(String hex) {
        if (hex == null)
            return null;
        assert hex.length() % 2 == 0;
        int len = hex.length();
        byte[] bytes = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            bytes[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4) + Character.digit(hex.charAt(i + 1), 16));
        }
        return bytes;
    }

    public static String format(byte[] bytes) {
        if (bytes == null)
            return null;
        final StringBuilder s = new StringBuilder(bytes.length * 2);
        for (byte aByte : bytes) {
            s.append(String.format("%02X", aByte & 0xff));
        }
        return s.toString();
    }

}
