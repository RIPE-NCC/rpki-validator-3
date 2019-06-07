package net.ripe.rpki.validator3.storage.lmdb;

import net.ripe.rpki.validator3.storage.IxMap;
import net.ripe.rpki.validator3.storage.data.Key;
import net.ripe.rpki.validator3.storage.encoding.Coder;
import org.lmdbjava.Env;

import java.io.Serializable;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

public interface Storage {
    <T> T writeTx(Function<LmdbTx.Write, T> f);

    void writeTx0(Consumer<LmdbTx.Write> c);

    <T> T readTx(Function<LmdbTx.Read, T> f);

    void readTx0(Consumer<LmdbTx.Read> c);

    Env<ByteBuffer> getEnv();

    String status();

    <T extends Serializable> LmdbIxMap<T> createIxMap(String name,
                                                      Map<String, Function<T, Set<Key>>> indexFunctions,
                                                      Class<T> c);

    <T extends Serializable> MultIxMap<T> createMultIxMap(String name, Coder<T> c);

    <T extends Serializable> LmdbIxMap<T> createIxMap(String name,
                                                      Map<String, Function<T, Set<Key>>> indexFunctions,
                                                      Coder<T> c);

    <T extends Serializable> IxMap<T> createSameSizeKeyIxMap(int keySize,
                                                             String name,
                                                             Map<String, Function<T, Set<Key>>> indexFunctions,
                                                             Coder<T> c);

    Lmdb.Stat getStat();

    Map<Long, Lmdb.TxInfo> getTxs();
}
