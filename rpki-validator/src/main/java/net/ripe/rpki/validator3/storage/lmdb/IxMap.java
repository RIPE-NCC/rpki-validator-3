package net.ripe.rpki.validator3.storage.lmdb;

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

    Optional<T> get(LmdbTx.Read txn, Key primaryKey);

    List<T> get(LmdbTx.Read txn, Set<Key> primaryKeys);

    Map<Key, T> getByIndex(String indexName, LmdbTx.Read tx, Key indexKey);

    Set<Key> getPkByIndex(String indexName, LmdbTx.Read tx, Key indexKey);

    Map<Key, T> getByIndexLess(String indexName, LmdbTx.Read tx, Key indexKey);

    Map<Key, T> getByIndexGreater(String indexName, LmdbTx.Read tx, Key indexKey);

    Set<Key> getByIndexLessPk(String indexName, LmdbTx.Read tx, Key indexKey);

    Set<Key> getByIndexGreaterPk(String indexName, LmdbTx.Read tx, Key indexKey);

    Map<Key, T> getByIndexMax(String indexName, LmdbTx.Read tx, Predicate<T> p);

    Map<Key, T> getByIndexMin(String indexName, LmdbTx.Read tx, Predicate<T> p);

    Set<Key> getPkByIndexMax(String indexName, LmdbTx.Read tx);

    Set<Key> getPkByIndexMin(String indexName, LmdbTx.Read tx);

    Optional<T> put(LmdbTx.Write tx, Key primaryKey, T value);

    boolean modify(LmdbTx.Write tx, Key primaryKey, Consumer<T> modifyValue);

    void delete(LmdbTx.Write tx, Key primaryKey);

    void onDelete(BiConsumer<LmdbTx.Write, Key> bf);

    void clear(LmdbTx.Write tx);

    LmdbIxBase.Sizes sizeInfo(LmdbTx.Read tx);
}
