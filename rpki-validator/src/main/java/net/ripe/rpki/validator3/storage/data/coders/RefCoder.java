package net.ripe.rpki.validator3.storage.data.coders;

import net.ripe.rpki.validator3.storage.data.Key;
import net.ripe.rpki.validator3.storage.data.Ref;

import java.io.Serializable;
import java.util.Map;

public class RefCoder<T extends Serializable> {

    private static final Tags tags = new Tags();
    private final static short TABLE_NAME_TAG = tags.unique(1);
    private final static short KEY_TAG = tags.unique(2);

    byte[] toBytes(Ref<T> ref) {
        final Encoded encoded = new Encoded();
        encoded.append(TABLE_NAME_TAG, Coders.toBytes(ref.getMapName()));
        encoded.append(KEY_TAG, ref.key().getBytes());
        return encoded.toByteArray();
    }

    Ref<T> fromBytes(byte[] bytes) {
        Map<Short, byte[]> content = Encoded.fromByteArray(bytes).getContent();
        return Ref.unsafe(
                Coders.toString(content.get(TABLE_NAME_TAG)),
                Key.of(content.get(KEY_TAG)));
    }
}
