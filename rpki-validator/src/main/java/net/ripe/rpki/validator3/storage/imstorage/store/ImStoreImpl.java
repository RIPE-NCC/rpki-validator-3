package net.ripe.rpki.validator3.storage.imstorage.store;

import net.ripe.rpki.validator3.storage.data.Key;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

public class ImStoreImpl<T> implements ImStore<T> {

    private final Map<Key, T> map;

    public ImStoreImpl() {
        this.map = new TreeMap<>(Comparator.comparing(Key::toByteIterable));
    }

    @Override
    public Collection<Map.Entry<Key,T>> entries() {
        return map.entrySet();
    }

    @Override
    public void delete(Key oik, T value) {
        map.remove(oik);
    }

    @Override
    public void put(Key primaryKey, T value) {
        map.put(primaryKey, value);
    }

    @Override
    public boolean containsKey(Key key) {
        return map.containsKey(key);
    }

    @Override
    public Set<Key> keys() {
        return map.keySet();
    }

    @Override
    public List<T> values() {
        return Collections.unmodifiableList(new ArrayList<>(map.values()));
    }

    @Override
    public Map<Key, T> getMap() {
        return map;
    }

    @Override
    public void clear() {
        map.clear();
    }

    @Override
    public int size() {
        return map.size();
    }

    @Override
    public T get(Key primaryKey) {
        return map.get(primaryKey);
    }

    public List<T> getByKeys(Collection<Key> idxKeys) {
        return idxKeys.stream().map(k->map.get(k)).collect(Collectors.toList());
    }
}
