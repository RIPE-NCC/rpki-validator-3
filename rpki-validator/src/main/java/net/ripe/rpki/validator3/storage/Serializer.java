package net.ripe.rpki.validator3.storage;

import java.nio.ByteBuffer;

public interface Serializer<T> {
    ByteBuffer toBytes(T t);
    T fromBytes(ByteBuffer bb);
}
