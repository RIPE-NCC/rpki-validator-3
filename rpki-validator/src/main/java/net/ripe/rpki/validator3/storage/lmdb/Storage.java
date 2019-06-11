package net.ripe.rpki.validator3.storage.lmdb;

import net.ripe.rpki.validator3.storage.IxMap;
import net.ripe.rpki.validator3.storage.Tx;
import net.ripe.rpki.validator3.storage.data.Key;
import net.ripe.rpki.validator3.storage.encoding.Coder;

import java.io.Serializable;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

public interface Storage {
    <T> T writeTx(Function<Tx.Write, T> f);

    void writeTx0(Consumer<Tx.Write> c);

    <T> T readTx(Function<Tx.Read, T> f);

    void readTx0(Consumer<Tx.Read> c);

    String status();

    <T extends Serializable> IxMap<T> createIxMap(String name,
                                                      Map<String, Function<T, Set<Key>>> indexFunctions,
                                                      Class<T> c);

    <T extends Serializable> LmdbMultIxMap<T> createMultIxMap(String name, Coder<T> c);

    <T extends Serializable> IxMap<T> createIxMap(String name,
                                                      Map<String, Function<T, Set<Key>>> indexFunctions,
                                                      Coder<T> c);

}
