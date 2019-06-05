package net.ripe.rpki.validator3.storage.lmdb;

import net.ripe.rpki.validator3.storage.Tx;
import net.ripe.rpki.validator3.storage.data.Key;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;

public interface IxMap<T extends Serializable> extends IxBase<T> {
    Optional<T> get(Key primaryKey);

    Optional<T> get(Tx.Read txn, Key primaryKey);

    List<T> get(Tx.Read txn, Set<Key> primaryKeys);

    Map<Key, T> getByIndex(String indexName, Tx.Read tx, Key indexKey);

    Set<Key> getPkByIndex(String indexName, Tx.Read tx, Key indexKey);

    Map<Key, T> getByIndexLess(String indexName, Tx.Read tx, Key indexKey);

    Map<Key, T> getByIndexGreater(String indexName, Tx.Read tx, Key indexKey);

    Set<Key> getByIndexLessPk(String indexName, Tx.Read tx, Key indexKey);

    Set<Key> getByIndexGreaterPk(String indexName, Tx.Read tx, Key indexKey);

    Map<Key, T> getByIndexMax(String indexName, Tx.Read tx, Predicate<T> p);

    Map<Key, T> getByIndexMin(String indexName, Tx.Read tx, Predicate<T> p);

    Set<Key> getPkByIndexMax(String indexName, Tx.Read tx);

    Set<Key> getPkByIndexMin(String indexName, Tx.Read tx);

    Optional<T> put(Tx.Write tx, Key primaryKey, T value);

    boolean modify(Tx.Write tx, Key primaryKey, Consumer<T> modifyValue);

    void delete(Tx.Write tx, Key primaryKey);

    void onDelete(BiConsumer<Tx.Write, Key> bf);

    void clear(Tx.Write tx);

    LmdbIxBase.Sizes sizeInfo(Tx.Read tx);
}
