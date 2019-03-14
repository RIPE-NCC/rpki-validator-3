package net.ripe.rpki.validator3.storage.lmdb;

import lombok.Value;

import java.nio.ByteBuffer;

@Value
public class Key {
    private byte[] key;

    public ByteBuffer toByteBuffer() {
        return ByteBuffer.wrap(key);
    }
}
