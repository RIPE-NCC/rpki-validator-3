package net.ripe.rpki.validator3.storage.lmdb;

import lombok.AllArgsConstructor;
import net.ripe.rpki.validator3.storage.Bytes;
import net.ripe.rpki.validator3.util.Hex;

import java.nio.ByteBuffer;

@AllArgsConstructor
public class Key {
    private final ByteBuffer key;

    public ByteBuffer toByteBuffer() {
        return key;
    }

    @Override
    public String toString() {
        return Hex.format(Bytes.toBytes(key));
    }
}
