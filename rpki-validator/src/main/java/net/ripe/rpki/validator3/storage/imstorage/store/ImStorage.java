package net.ripe.rpki.validator3.storage.imstorage.store;

import lombok.Getter;
import net.ripe.rpki.validator3.storage.IxBase;
import net.ripe.rpki.validator3.storage.IxMap;
import net.ripe.rpki.validator3.storage.MultIxMap;
import net.ripe.rpki.validator3.storage.Storage;
import net.ripe.rpki.validator3.storage.Tx;
import net.ripe.rpki.validator3.storage.data.Key;
import net.ripe.rpki.validator3.storage.encoding.Coder;
import net.ripe.rpki.validator3.storage.encoding.CoderFactory;
import net.ripe.rpki.validator3.storage.imstorage.ImIxMap;
import net.ripe.rpki.validator3.storage.imstorage.ImTx;
import net.ripe.rpki.validator3.storage.imstorage.ImMultiIxMap;
import net.ripe.rpki.validator3.storage.xodus.Xodus;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

public abstract class ImStorage implements Storage {


    @Override
    public <T> T writeTx(Function<Tx.Write, T> f) {
        ImTx.Write tx = new ImTx.Write();
//        txs.put(tx.getId(), new Xodus.TxInfo(tx));
        try {
            T result = f.apply(tx);
            if (tx.getAtCommit() != null) {
                tx.getAtCommit().forEach(r -> {
                    try {
                        r.run();
                    } catch (Exception ignored) {
                        // this is just to keep the loop going, every Runnable
                        // has to take care of exceptions themselves
                    }
                });
            }
            return result;
        } finally {
//            txs.remove(tx.getId());
        }

    }

    @Override
    public void writeTx0(Consumer<Tx.Write> c) {
        writeTx(tx -> {
            c.accept(tx);
            return null;
        });
    }

    @Override
    public <T> T readTx(Function<Tx.Read, T> f) {
        ImTx.Read tx = new ImTx.Read();
        return f.apply(tx);
    }

    @Override
    public void readTx0(Consumer<Tx.Read> c) {
        readTx(t-> {c.accept(t); return null;});
    }

    @Override
    public String status() {
        return null;
    }

    @Override
    public <T extends Serializable> IxMap<T> createIxMap(String name, Map<String, Function<T, Set<Key>>> indexFunctions, Class<T> c) {
        return createIxMap(name, indexFunctions, CoderFactory.makeCoder(c));
    }

    @Override
    public <T extends Serializable> MultIxMap<T> createMultIxMap(String name, Coder<T> c) {
        ImMultiIxMap<T> ixMap = new ImMultiIxMap(name, c);
        ixMaps.put(name, ixMap);
        return ixMap;
    }

    @Override
    public <T extends Serializable> IxMap<T> createIxMap(String name, Map<String, Function<T, Set<Key>>> indexFunctions, Coder<T> c) {
        ImIxMap<T> ixMap = new ImIxMap(this, name, c, indexFunctions);
        ixMaps.put(name, ixMap);
        return ixMap;
    }

    public Set<String> getDbNames(){
        return ixMaps.keySet();
    }

    @Override
    public void gc() {

    }

    @NotNull
    @Override
    public Map<String, String> getDbStats() {
        return null;
    }

    @Getter
    private final Map<Long, Xodus.TxInfo> txs = new ConcurrentHashMap<>();

    @Getter
    protected final Map<String, IxBase<?>> ixMaps = new ConcurrentHashMap<>();

    public abstract <T extends Serializable> Map<String, ImStoreMultiImpl> createIndexes(String name, Map<String, Function<T, Set<Key>>> indexFunctions);
}
