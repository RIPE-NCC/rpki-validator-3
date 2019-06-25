package net.ripe.rpki.validator3.storage.imstorage.store;

import net.ripe.rpki.validator3.storage.data.Key;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

@Component
@Primary
public class ImStorageImpl extends ImStorage {

    @Override
    public <T extends Serializable> Map<String, ImStoreMultiImpl> createIndexes(String name, Map<String, Function<T, Set<Key>>> indexFunctions) {
        ConcurrentHashMap<String, ImStoreMultiImpl> indexes = new ConcurrentHashMap<>();
        indexFunctions.keySet().forEach(n -> indexes.put(n, new ImStoreMultiImpl()));
        return indexes;
    }
}
