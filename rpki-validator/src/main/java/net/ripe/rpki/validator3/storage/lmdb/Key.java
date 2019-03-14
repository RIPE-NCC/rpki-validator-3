package net.ripe.rpki.validator3.storage.lmdb;

import lombok.Value;

import java.nio.ByteBuffer;

@Value
public class Key {
    private final ByteBuffer key;

    public ByteBuffer toByteBuffer() {
        return key;
    }
}
