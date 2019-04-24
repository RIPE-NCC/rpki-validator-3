package net.ripe.rpki.validator3.storage.data.coders;

public interface Coder<T> {
    byte[] toBytes(T t);
    T fromBytes(byte[] bytes);
}
