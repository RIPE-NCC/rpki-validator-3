package net.ripe.rpki.validator3.storage.data.coders;

import net.ripe.rpki.validator3.storage.Bytes;
import net.ripe.rpki.validator3.storage.data.Key;
import net.ripe.rpki.validator3.storage.data.Ref;

import java.io.Serializable;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class RefCoder<T extends Serializable> {
    byte[] toBytes(Ref<T> ref) {
        byte[] bytes = ref.getMapName().getBytes(StandardCharsets.UTF_8);
        byte[] keyBytes = ref.key().getBytes();

        ByteBuffer bb = ByteBuffer.allocate(bytes.length + keyBytes.length + Integer.BYTES * 2);
        bb.putInt(bytes.length);
        bb.put(bytes);
        bb.putInt(bytes.length);
        bb.put(keyBytes);

        return Bytes.toBytes(bb);
    }

    Ref<T> fromBytes(byte[] bytes) {
        ByteBuffer bb = ByteBuffer.wrap(bytes);
        byte[] nameBytes = readBytes(bb);
        byte[] keyBytes = readBytes(bb);
        return Ref.unsafe(new String(nameBytes, StandardCharsets.UTF_8), Key.of(keyBytes));
    }

    private byte[] readBytes(ByteBuffer bb) {
        int len = bb.getInt();
        byte[] bytes = new byte[len];
        bb.get(bytes);
        return bytes;
    }
}
