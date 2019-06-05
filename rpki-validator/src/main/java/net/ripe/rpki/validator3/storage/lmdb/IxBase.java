package net.ripe.rpki.validator3.storage.lmdb;

import net.ripe.rpki.validator3.storage.data.Key;

import java.io.Serializable;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

public interface IxBase<T extends Serializable> {

    LmdbTx.Read readTx();

    boolean exists(LmdbTx.Read tx, Key key);

    Set<Key> keys(LmdbTx.Read tx);

    List<T> values(LmdbTx.Read tx);

    Map<Key, T> all(LmdbTx.Read tx);

    void clear(LmdbTx.Write tx);

    T toValue(ByteBuffer bb);

    void forEach(LmdbTx.Read tx, BiConsumer<Key, ByteBuffer> c);

    long size(LmdbTx.Read tx);

    LmdbIxBase.Sizes sizeInfo(LmdbTx.Read tx);

    String getName();
}
