package net.ripe.rpki.validator3.storage.imstorage;

import lombok.Getter;
import net.ripe.rpki.validator3.storage.IxBase;
import net.ripe.rpki.validator3.storage.Tx;
import net.ripe.rpki.validator3.storage.data.Key;
import net.ripe.rpki.validator3.storage.encoding.Coder;
import net.ripe.rpki.validator3.storage.imstorage.store.ImStore;
import net.ripe.rpki.validator3.storage.imstorage.store.ImStoreImpl;
import net.ripe.rpki.validator3.storage.imstorage.store.ImStoreMultiImpl;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

public abstract class ImIxBase<T extends Serializable> implements IxBase<T> {

    @Getter
    private final String name;

    protected final ImStore<T> mainStore;
    final Coder<T> coder;

    public ImIxBase(String name, Coder<T> coder) {
        this.name = name;
        this.coder = coder;

        if (withDuplicates()) {
            mainStore = new ImStoreMultiImpl();
        } else {
            mainStore = new ImStoreImpl();
        }
    }

    protected abstract boolean withDuplicates();

    @Override
    public Tx.Read readTx() {
        return ImTx.readTx();
    }

    @Override
    public boolean exists(Tx.Read tx, Key key) {
        return mainStore.containsKey(key);
    }

    @Override
    public Set<Key> keys(Tx.Read tx) {
        return mainStore.keys();
    }

    @Override
    public List<T> values(Tx.Read tx) {
        return Collections.unmodifiableList(new ArrayList<>(mainStore.values()));
    }

    @Override
    public Map<Key, T> all(Tx.Read tx) {
        return Collections.unmodifiableMap(mainStore.getMap());
    }

    @Override
    public void clear(Tx.Write tx) {
        mainStore.clear();
    }

    @Override
    public T toValue(byte[] bb) {
        return coder.fromBytes(bb);
    }

    @Override
    public void forEach(Tx.Read tx, BiConsumer<Key, byte[]> c) {
       throw new RuntimeException("We're not serializing to byte in memory, use forEachT");
    }

    @Override
    public void forEachT(Tx.Read tx, BiConsumer<Key, T> c) {
        mainStore.entries().forEach(e-> c.accept(e.getKey(), e.getValue()));
    }

    @Override
    public long size(Tx.Read tx) {
        return mainStore.size();
    }

    @Override
    public Sizes sizeInfo(Tx.Read tx) {
        return new Sizes(mainStore.size(), size(mainStore));
    }


    @Override
    public String getName() {
        return name;
    }

    public static long size(ImStore map) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(baos);
            oos.writeObject(map);
            oos.close();
            return baos.size();
        } catch (IOException e) {
            return -1l;
        }
    }


    ImStore<T> getMainStore() {
        return mainStore;
    }
}
