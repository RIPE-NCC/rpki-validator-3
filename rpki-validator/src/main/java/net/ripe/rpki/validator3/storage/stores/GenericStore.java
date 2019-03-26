package net.ripe.rpki.validator3.storage.stores;

import net.ripe.rpki.validator3.storage.data.Key;
import net.ripe.rpki.validator3.storage.data.Ref;
import net.ripe.rpki.validator3.storage.lmdb.Tx;
import org.apache.commons.lang3.tuple.Pair;

import java.util.List;
import java.util.function.Consumer;

public interface GenericStore<T> {
    Ref<T> makeRef(Tx.Read tx, Key key);
    List<T> values(Tx.Read tx);
    void forEach(Tx.Read tx, Consumer<Pair<Key, T>> c);
}
