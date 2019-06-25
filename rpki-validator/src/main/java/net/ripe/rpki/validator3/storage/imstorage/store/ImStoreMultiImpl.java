package net.ripe.rpki.validator3.storage.imstorage.store;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import net.ripe.rpki.validator3.storage.data.Key;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;

public class ImStoreMultiImpl<T> implements ImStore<T> {

    private final Multimap<Key, T> multimap;

    @Override
    public void put(Key primaryKey, T value) {
        multimap.put(primaryKey, value);
    }

    public ImStoreMultiImpl() {
        this.multimap = HashMultimap.create();
    }

    @Override
    public boolean containsKey(Key key) {
        return multimap.containsKey(key);
    }

    @Override
    public void delete(Key key, T value) {
        multimap.remove(key, value);
    }

    @Override
    public Set<Key> keys() {
        return multimap.keySet();
    }

    @Override
    public List<T> values() {
        return Collections.unmodifiableList(new ArrayList<>(multimap.values()));
    }

    @Override
    public Map<Key, T> getMap() {
        throw new RuntimeException("Shouldn't force multimap into normal map, values will be multiple");
    }

    @Override
    public void clear() {
        multimap.clear();
    }

    @Override
    public int size() {
        return 0;
    }

    @Override
    public T get(Key primaryKey) {
        // Why would you want to get an element from multimap?
        return multimap.get(primaryKey).iterator().next();
    }


    public List<T> toPrimaryKeys(Collection<Key> idxKeys) {
        return idxKeys.stream().flatMap(k->multimap.get(k).stream()).collect(Collectors.toList());
    }

    public Collection<T> getPrimaryKeys(Key indexKey) {
       return multimap.get(indexKey);
    }


    public SortedSet<Key> sortedKeys(){
        TreeSet<Key> ordered = new TreeSet<>(Comparator.comparing(Key::toByteIterable));
        ordered.addAll(keys());
        return ordered;
    }

    public SortedSet<Key> sortedKeysRev(){
        TreeSet<Key> ordered = new TreeSet<>(Comparator.comparing(Key::toByteIterable).reversed());
        ordered.addAll(keys());
        return ordered;
    }

    public boolean exists(Key key, T value){
        return multimap.containsEntry(key, value);
    }

    public void delete(Key key){
        multimap.removeAll(key);
    }

    @Override
    public Collection<Map.Entry<Key, T>> entries() {
        return multimap.entries();
    }
}
