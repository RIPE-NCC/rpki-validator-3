package net.ripe.rpki.validator3.storage.lmdb;

import net.ripe.rpki.validator3.storage.Serializer;
import org.lmdbjava.Cursor;
import org.lmdbjava.CursorIterator;
import org.lmdbjava.Dbi;
import org.lmdbjava.Env;
import org.lmdbjava.KeyRange;
import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static org.lmdbjava.DbiFlags.MDB_CREATE;
import static org.lmdbjava.DbiFlags.MDB_DUPSORT;

public class Store<T> {
    private final Env<ByteBuffer> env;
    private final Dbi<ByteBuffer> mainDb;
    private final Serializer<T> serializer;
    private final Map<String, Dbi<ByteBuffer>> indexes;
    private final Map<String, Function<T, Key>> indexFunctions;

    public Store(final Env<ByteBuffer> env,
                 final String name,
                 final Serializer<T> serializer,
                 final Map<String, Function<T, Key>> indexFunctions) {
        this.env = env;
        this.serializer = serializer;
        this.mainDb = env.openDbi(name + ":main", MDB_CREATE);
        this.indexFunctions = indexFunctions;
        this.indexes = new HashMap<>();
        indexFunctions.forEach((n, idxFun) ->
                indexes.put(n, env.openDbi(name + ":idx:" + n, MDB_CREATE, MDB_DUPSORT)));
    }

    public void put(Key primaryKey, T value) {
        checkKeyAndValue(primaryKey, value);
        try (Txn<ByteBuffer> txn = env.txnWrite()) {
            put(txn, primaryKey, value);
            txn.commit();
        }
    }

    private void checkKeyAndValue(Key primaryKey, T value) {
        checkNotNull(primaryKey, "Key is null");
        checkNotNull(value, "Value is null");
    }

    public void put(Txn<ByteBuffer> txn, Key primaryKey, T value) {
        checkKeyAndValue(primaryKey, value);
        final ByteBuffer pkBuf = primaryKey.toByteBuffer();
        mainDb.put(txn, pkBuf, serializer.toBytes(value));
        indexFunctions.forEach((n, idxFun) -> {
            final Key indexKey = idxFun.apply(value);
            final Dbi<ByteBuffer> indexDb = indexes.get(n);
            try (Cursor<ByteBuffer> c = indexDb.openCursor(txn)) {
                c.put(indexKey.toByteBuffer(), pkBuf);
            }
        });
    }

    public Optional<T> get(Txn<ByteBuffer> txn, Key primaryKey) {
        checkNotNull(primaryKey, "Key is null");
        ByteBuffer bb = mainDb.get(txn, primaryKey.toByteBuffer());
        return bb == null ?
                Optional.empty() :
                Optional.of(serializer.fromBytes(bb));
    }

    public List<T> getByIndex(String indexName, Txn<ByteBuffer> txn, Key indexKey) {
        checkNotNull(indexKey, "Index key is null");
        final Dbi<ByteBuffer> index = indexes.get(indexName);
        if (index == null) {
            return Collections.emptyList();
        }

        final ByteBuffer idxKey = indexKey.toByteBuffer();
        final CursorIterator<ByteBuffer> iterator = index.iterate(txn, KeyRange.closed(idxKey, idxKey));
        final List<T> values = new ArrayList<>();
        while (iterator.hasNext()) {
            final ByteBuffer bb = mainDb.get(txn, iterator.next().val());
            if (bb != null) {
                values.add(serializer.fromBytes(bb));
            }
        }
        return values;
    }

    private static void checkNotNull(Object v, String s) {
        if (v == null) {
            throw new NullPointerException(s);
        }
    }
}
