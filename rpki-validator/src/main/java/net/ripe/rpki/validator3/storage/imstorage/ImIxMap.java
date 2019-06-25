package net.ripe.rpki.validator3.storage.imstorage;

import net.ripe.rpki.validator3.storage.IxMap;
import net.ripe.rpki.validator3.storage.OnDeleteRestrictException;
import net.ripe.rpki.validator3.storage.Tx;
import net.ripe.rpki.validator3.storage.data.Key;
import net.ripe.rpki.validator3.storage.encoding.Coder;
import net.ripe.rpki.validator3.storage.imstorage.store.ImStorage;
import net.ripe.rpki.validator3.storage.imstorage.store.ImStore;
import net.ripe.rpki.validator3.storage.imstorage.store.ImStoreMultiImpl;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static lombok.Lombok.checkNotNull;

public class ImIxMap<T extends Serializable> extends ImIxBase<T> implements IxMap<T> {

    private final Map<String, ImStoreMultiImpl> indexes;
    private final Map<String, Function<T, Set<Key>>> indexFunctions;
    private final List<BiConsumer<Tx.Write, Key>> onDeleteTriggers = new ArrayList<>();

    public ImIxMap(final ImStorage imStorage,
                   final String name,
                   final Coder<T> coder,
                   final Map<String, Function<T, Set<Key>>> indexFunctions) {
        super(name, coder);
        this.indexes = imStorage.createIndexes(name, indexFunctions);
        this.indexFunctions = indexFunctions;
    }

    @Override
    protected boolean withDuplicates() {
        return false;
    }

    @Override
    public Optional<T> get(Key primaryKey) {
        return Optional.of(getMainStore().get(primaryKey));
    }

    @Override
    public Optional<T> get(Tx.Read txn, Key primaryKey) {
        return Optional.ofNullable(getMainStore().get(primaryKey));
    }

    @Override
    public List<T> get(Tx.Read txn, Set<Key> primaryKeys) {
        return getMainStore().toPrimaryKeys(primaryKeys);
    }

    @Override
    public Map<Key, T> getByIndex(String indexName, Tx.Read tx, Key indexKey) {
       return values(tx, getIdx(indexName).getPrimaryKeys(indexKey));
    }

    public Map<Key, T> values(Tx.Read tx, Collection<Key> pks) {
        final Map<Key, T> m = new HashMap<>();
        pks.forEach(pk -> get(tx, pk).ifPresent(v -> m.put(pk, v)));
        return m;
    }


    @Override
    public Set<Key> getPkByIndex(String indexName, Tx.Read tx, Key indexKey) {
        return new HashSet<>(getIdx(indexName).getPrimaryKeys(indexKey));
    }

    @Override
    public Map<Key, T> getByIndexLessThan(String indexName, Tx.Read tx, Key indexKey) {
        return values(tx, getPkByIndexLessThan(indexName, tx, indexKey));
    }

    @Override
    public Map<Key, T> getByIndexNotLessThan(String indexName, Tx.Read tx, Key indexKey) {
        return values(tx, getPkByIndexGreaterThan(indexName, tx, indexKey));
    }

    @Override
    public Set<Key> getPkByIndexLessThan(String indexName, Tx.Read tx, Key indexKey) {
        ImStoreMultiImpl<Key> index = getIdx(indexName);
        return index.toPrimaryKeys(index.sortedKeys().headSet(indexKey)).stream().collect(Collectors.toSet());
    }

    @Override
    public Set<Key> getPkByIndexGreaterThan(String indexName, Tx.Read tx, Key indexKey) {
        ImStoreMultiImpl<Key> index = getIdx(indexName);
        return index.toPrimaryKeys(index.sortedKeys().tailSet(indexKey)).stream().collect(Collectors.toSet());
    }

    @Override
    public Map<Key, T> getByIdxDescendingWhere(String indexName, Tx.Read tx, Predicate<T> p) {
        Map<Key,T> result = new HashMap<>();
        for(Key k  : getIdx(indexName).toPrimaryKeys(getIdx(indexName).sortedKeysRev())) {
            T value = mainStore.get(k);
            if (value != null && p.test(value)) {

                // Wait but there is more, why do I have to break here?
                result.put(k, value);
                break;
            }
        }

        return result;
    }

    @Override
    public Map<Key, T> getByIdxAscendingWhere(String indexName, Tx.Read tx, Predicate<T> p) {
        Map<Key,T> result = new HashMap<>();
        for(Key k  : getIdx(indexName).toPrimaryKeys(getIdx(indexName).sortedKeys())) {
            T value = mainStore.get(k);
            if (value != null && p.test(value)) {

                // Wait but there is more, why do I have to break here?
                result.put(k, value);
                break;
            }
        }

        return result;
    }

    @Override
    public Optional<T> put(Tx.Write tx, Key primaryKey, T value) {
        checkNotNull(primaryKey, "PK is null");
        checkNotNull(value, "Value is null");

        final Optional<T> oldValue = get(tx, primaryKey);

        getMainStore().put(primaryKey, value);

        if (oldValue.isPresent()) {
            indexFunctions.forEach((idxName, idxFun) -> {
                final Set<Key> oldIndexKeys = idxFun.apply(oldValue.get());
                final Set<Key> indexKeys = idxFun.apply(value).stream()
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());
                final ImStore index = getIdx(idxName);
                oldIndexKeys.stream()
                        .filter(oik -> !indexKeys.contains(oik))
                        .forEach(oik -> {
                            index.delete(oik, primaryKey);
                        });

                indexKeys.stream()
                        .filter(ik -> !oldIndexKeys.contains(ik))
                        .forEach(ik -> {
                            index.put(ik, primaryKey);
                        });
            });
            return oldValue;
        }
        indexFunctions.forEach((idxName, idxFun) -> {
            final Set<Key> indexKeys = idxFun.apply(value).stream()
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            indexKeys.forEach(ik -> getIdx(idxName).put(ik, primaryKey));
        });

        return Optional.empty();
    }

    private ImStoreMultiImpl<Key> getIdx(String idxName) {
        return indexes.get(idxName);
    }

    @Override
    public boolean modify(Tx.Write tx, Key primaryKey, Consumer<T> modifyValue) {
        return false;
    }

    @Override
    public void delete(Tx.Write tx, Key primaryKey) {
        checkNotNull(primaryKey, "Key is null");

        final ImStore<T> mainDb = getMainStore();
        T value = mainDb.get(primaryKey);

        if (indexFunctions.isEmpty()) {
            mainDb.delete(primaryKey, value);
        } else {

            if (value != null) {

                mainDb.delete(primaryKey, value);
                indexFunctions.forEach((idxName, idxFun) ->
                        idxFun.apply(value).forEach(ix -> {
                            getIdx(idxName).delete(ix, primaryKey);
                        }));
            }
        }
        try {
            onDeleteTriggers.forEach(bf -> bf.accept(tx, primaryKey));
        } catch (OnDeleteRestrictException o) {
            tx.abort();
        }
    }

    @Override
    public void onDelete(BiConsumer<Tx.Write, Key> bf) {
        onDeleteTriggers.add(bf);
    }
}
