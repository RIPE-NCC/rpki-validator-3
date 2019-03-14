package net.ripe.rpki.validator3.storage;

import java.nio.ByteBuffer;

public class Bytes {
    public static ByteBuffer toDirectBuffer(byte[] bytes) {
        ByteBuffer bb = ByteBuffer.allocateDirect(bytes.length);
        bb.put(bytes).flip();
        return bb;
    }

    public static byte[] toBytes(ByteBuffer bb) {
        byte[] bytes = new byte[bb.remaining()];
        bb.get(bytes);
        bb.flip();
        return bytes;
    }
}
