/**
 * The BSD License
 *
 * Copyright (c) 2010-2018 RIPE NCC
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *   - Redistributions of source code must retain the above copyright notice,
 *     this list of conditions and the following disclaimer.
 *   - Redistributions in binary form must reproduce the above copyright notice,
 *     this list of conditions and the following disclaimer in the documentation
 *     and/or other materials provided with the distribution.
 *   - Neither the name of the RIPE NCC nor the names of its contributors may be
 *     used to endorse or promote products derived from this software without
 *     specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package net.ripe.rpki.validator3.storage.imstorage;

import com.rits.cloning.Cloner;
import net.ripe.rpki.validator3.storage.IxMap;
import net.ripe.rpki.validator3.storage.OnDeleteRestrictException;
import net.ripe.rpki.validator3.storage.Tx;
import net.ripe.rpki.validator3.storage.data.Key;
import net.ripe.rpki.validator3.storage.encoding.Coder;
import net.ripe.rpki.validator3.storage.imstorage.store.ImStorage;
import net.ripe.rpki.validator3.storage.imstorage.store.ImStore;
import net.ripe.rpki.validator3.storage.imstorage.store.ImStoreMultiImpl;
import org.jetbrains.annotations.NotNull;

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
import java.util.SortedSet;
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
        return getMainStore().getByKeys(primaryKeys);
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
        return new HashSet<>(index.getByKeys(index.sortedKeys().headSet(indexKey)));
    }

    @Override
    public Set<Key> getPkByIndexGreaterThan(String indexName, Tx.Read tx, Key indexKey) {
        ImStoreMultiImpl<Key> index = getIdx(indexName);
        return new HashSet<>(index.getByKeys(index.sortedKeys().tailSet(indexKey)));
    }

    @Override
    public Map<Key, T> getByIdxDescendingWhere(String indexName, Tx.Read tx, Predicate<T> p) {
        ImStoreMultiImpl<Key> idx = getIdx(indexName);
        SortedSet<Key> descKeys = idx.sortedKeysRev();

        return valuesFromKeysWithPredicate(p, idx, descKeys);
    }

    @Override
    public Map<Key, T> getByIdxAscendingWhere(String indexName, Tx.Read tx, Predicate<T> p) {
        ImStoreMultiImpl<Key> idx = getIdx(indexName);
        SortedSet<Key> descKeys = idx.sortedKeys();
        return valuesFromKeysWithPredicate(p, idx, descKeys);
    }

    @NotNull
    private Map<Key, T> valuesFromKeysWithPredicate(Predicate<T> p, ImStoreMultiImpl<Key> idx, SortedSet<Key> descKeys) {
        Map<Key,T> result = new HashMap<>();
        for(Key idxKey : descKeys) {
            for(Key pk : idx.getPrimaryKeys(idxKey)) {
                T value = mainStore.get(pk);
                if(value != null && p.test(value)){
                    result.put(pk, value);
                }
            }
            if(!result.isEmpty()) break;
        }
        return result;
    }

    @Override
    public Optional<T> put(Tx.Write tx, Key primaryKey, T newValue) {
        checkNotNull(primaryKey, "PK is null");
        checkNotNull(newValue, "Value is null");

        final T value = cloner.deepClone(newValue);
        final Optional<T> oldValue = get(tx, primaryKey);

        getMainStore().put(primaryKey, value);

        if (oldValue.isPresent()) {
            indexFunctions.forEach((idxName, idxFun) -> {
                final Set<Key> oldIndexKeys = idxFun.apply(oldValue.get());
                final Set<Key> indexKeys = idxFun.apply(value).stream()
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());
                final ImStoreMultiImpl<Key> index = getIdx(idxName);
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

    static Cloner cloner = new Cloner();
}
