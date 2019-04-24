package net.ripe.rpki.validator3.storage.data.coders;

import net.ripe.rpki.validator3.storage.data.Base;
import net.ripe.rpki.validator3.storage.data.Key;

import java.time.Instant;
import java.util.Map;

public class BaseCoder {

    private final static short ID_TAG = Coders.uniqueTag(1);
    private final static short CREATED_AT = Coders.uniqueTag(2);
    private final static short UPDATED_AT = Coders.uniqueTag(3);

    public static void toBytes(Base base, Encoded encoded) {
        encoded.append(ID_TAG, base.key().getBytes());
        Instant createdAt = base.getCreatedAt();
        if (createdAt != null) {
            encoded.append(CREATED_AT, Coders.toBytes(createdAt));
        }
        Instant updatedAt = base.getUpdatedAt();
        if (updatedAt != null) {
            encoded.append(UPDATED_AT, Coders.toBytes(updatedAt));
        }
    }

    public static void fromBytes(byte[] bytes, Base base) {
        Map<Short, byte[]> content = Encoded.fromByteArray(bytes).getContent();
        base.setId(Key.of(content.get(ID_TAG)));
        Encoded.field(content, CREATED_AT).ifPresent(b -> base.setCreatedAt(Coders.toInstant(b)));
        Encoded.field(content, UPDATED_AT).ifPresent(b -> base.setUpdatedAt(Coders.toInstant(b)));
    }

    public static void fromBytes(Map<Short, byte[]> content, Base base) {
        base.setId(Key.of(content.get(ID_TAG)));
        Encoded.field(content, CREATED_AT).ifPresent(b -> base.setCreatedAt(Coders.toInstant(b)));
        Encoded.field(content, UPDATED_AT).ifPresent(b -> base.setUpdatedAt(Coders.toInstant(b)));
    }
}
