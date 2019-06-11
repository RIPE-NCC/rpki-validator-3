package net.ripe.rpki.validator3.storage;

import net.ripe.rpki.validator3.storage.data.Key;

import java.io.Serializable;
import java.util.List;

public interface MultIxMap<T extends Serializable> extends IxBase<T> {
    List<T> get(Tx.Read tx, Key primaryKey);

    int count(Tx.Read tx, Key primaryKey);

    void put(Tx.Write tx, Key primaryKey, T value);

    void delete(Tx.Write tx, Key primaryKey);

    void delete(Tx.Write tx, Key primaryKey, T value);
}
