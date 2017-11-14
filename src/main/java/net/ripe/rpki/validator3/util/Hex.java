package net.ripe.rpki.validator3.util;

public class Hex {
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
