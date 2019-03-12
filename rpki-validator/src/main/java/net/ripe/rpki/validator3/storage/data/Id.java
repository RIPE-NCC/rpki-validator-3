package net.ripe.rpki.validator3.storage.data;

import com.fasterxml.uuid.Generators;
import lombok.Value;

import java.nio.ByteBuffer;
import java.util.UUID;

import static java.nio.ByteBuffer.allocateDirect;

@Value
public class Id<T> {
    private final byte[] id;

    public static <T> Id<T> generate() {
        UUID uuid = Generators.timeBasedGenerator().generate();
        final ByteBuffer key = allocateDirect(16);
        key.putLong(uuid.getMostSignificantBits());
        key.putLong(uuid.getLeastSignificantBits());
        key.flip();
        return new Id<>(key.array());
    }
}
