package net.ripe.rpki.validator3.storage.lmdb;

import net.ripe.rpki.validator3.storage.Tx;
import net.ripe.rpki.validator3.storage.data.Key;

import java.io.Serializable;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

public interface IxBase<T extends Serializable> {

    Tx.Read readTx();

    boolean exists(Tx.Read tx, Key key);

    Set<Key> keys(Tx.Read tx);

    List<T> values(Tx.Read tx);

    Map<Key, T> all(Tx.Read tx);

    void clear(Tx.Write tx);

    T toValue(byte[] bb);

    void forEach(Tx.Read tx, BiConsumer<Key, byte[]> c);

    long size(Tx.Read tx);

    LmdbIxBase.Sizes sizeInfo(Tx.Read tx);

    String getName();
}
