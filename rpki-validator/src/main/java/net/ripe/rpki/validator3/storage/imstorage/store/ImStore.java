package net.ripe.rpki.validator3.storage.imstorage.store;

import net.ripe.rpki.validator3.storage.data.Key;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface ImStore<T> {

    boolean containsKey(Key key);

    Set<Key> keys() ;

    List<T> values() ;

    Map<Key, T> getMap() ;

    void clear() ;

    int size();

    T get(Key primaryKey);

    List<T> toPrimaryKeys(Collection<Key> primaryKeys);

    void put(Key primaryKey, T value);

    void delete(Key oik, T value);

    Collection<Map.Entry<Key,T>> entries();
}
