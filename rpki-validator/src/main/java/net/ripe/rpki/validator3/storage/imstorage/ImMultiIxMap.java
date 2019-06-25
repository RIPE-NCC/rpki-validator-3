package net.ripe.rpki.validator3.storage.imstorage;

import net.ripe.rpki.validator3.storage.MultIxMap;
import net.ripe.rpki.validator3.storage.Tx;
import net.ripe.rpki.validator3.storage.data.Key;
import net.ripe.rpki.validator3.storage.encoding.Coder;
import net.ripe.rpki.validator3.storage.imstorage.store.ImStoreMultiImpl;
import org.apache.commons.lang3.tuple.Pair;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class ImMultiIxMap<T extends Serializable> extends ImIxBase<T> implements MultIxMap<T> {

    private final String name;
    private final ImStoreMultiImpl<T> mStore;
    private final Coder<T> coder;

    public ImMultiIxMap(String name, Coder<T> coder) {
        super(name, coder);
        this.name = name;
        this.coder = coder;
        this.mStore = (ImStoreMultiImpl<T>)getMainStore();

    }

    @Override
    public List<T> get(Tx.Read tx, Key primaryKey) {
        return new ArrayList<>(mStore.getPrimaryKeys(primaryKey));
    }

    @Override
    public int count(Tx.Read tx, Key idxKey) {
        return mStore.getPrimaryKeys(idxKey).size();
    }

    @Override
    public void put(Tx.Write tx, Key primaryKey, T value) {
        mStore.put(primaryKey, value);
    }

    @Override
    public void delete(Tx.Write tx, Key key) {
        mStore.delete(key);
    }

    @Override
    public void delete(Tx.Write tx, Key primaryKey, T value) {
        mStore.delete(primaryKey, value);
    }

    @Override
    public void deleteBatch(Tx.Write tx, List<Pair<Key, T>> toDelete) {
        toDelete.forEach(kp -> delete(tx, kp.getKey(), kp.getValue()));
    }

    @Override
    public boolean exists(Tx.Read tx, Key pk, T value) {
        return mStore.exists(pk, value);
    }

    @Override
    protected boolean withDuplicates() {
        return true;
    }
}
