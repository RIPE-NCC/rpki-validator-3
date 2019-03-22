package net.ripe.rpki.validator3.storage.lmdb;

import lombok.Getter;
import net.ripe.rpki.validator3.storage.Coder;
import org.lmdbjava.Cursor;
import org.lmdbjava.CursorIterator;
import org.lmdbjava.Dbi;
import org.lmdbjava.DbiFlags;
import org.lmdbjava.Env;
import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public abstract class IxBase<T> {
    private final Env<ByteBuffer> env;
    @Getter
    private final String name;

    protected final Dbi<ByteBuffer> mainDb;
    protected final Coder<T> coder;
    protected final Map<String, Dbi<ByteBuffer>> indexes;
    protected final Map<String, Function<T, Key>> indexFunctions;

    public IxBase(final Env<ByteBuffer> env,
                  final String name,
                  final Coder<T> coder,
                  final Map<String, Function<T, Key>> indexFunctions) {
        this.env = env;
        this.name = name;
        this.coder = coder;
        this.indexes = new HashMap<>();
        this.indexFunctions = indexFunctions;
        this.mainDb = env.openDbi(name + ":main", getMainDbCreateFlags());

        // TODO Add index management, reindexing if the index set has changed
        indexFunctions.forEach((n, idxFun) ->
                indexes.put(n, env.openDbi(name + ":idx:" + n, getIndexDbiFlags())));
    }

    public void reindex() {
        try (Tx.Write<ByteBuffer> tx = writeTx()) {
            dropIndexes(tx);
            try (final Cursor<ByteBuffer> cursor = mainDb.openCursor(tx.txn())) {
                do {
                    final T value = coder.fromBytes(cursor.val());
                    indexFunctions.forEach((n, idxFun) -> {
                        final Key indexKey = idxFun.apply(value);
                        try (Cursor<ByteBuffer> c = indexes.get(n).openCursor(tx.txn())) {
                            c.put(indexKey.toByteBuffer(), indexKey.toByteBuffer());
                        }
                    });
                }
                while (cursor.next());
            }
        }
    }

    private void dropIndexes(Tx.Write<ByteBuffer> tx) {
        indexes.forEach((name, db) -> db.drop(tx.txn()));
    }

    protected abstract DbiFlags[] getMainDbCreateFlags();

    protected abstract DbiFlags[] getIndexDbiFlags();

    protected static void checkNotNull(Object v, String s) {
        if (v == null) {
            throw new NullPointerException(s);
        }
    }

    public Tx.Read<ByteBuffer> readTx() {
        return Tx.read(env);
    }

    public Tx.Write<ByteBuffer> writeTx() {
        return Tx.write(env);
    }

    public void delete(Key primaryKey) {
        checkNotNull(primaryKey, "Key is null");
        Tx.use(Tx.write(env), tx -> delete(tx, primaryKey));
    }

    public void delete(Tx.Write<ByteBuffer> tx, Key primaryKey) {
        checkNotNull(primaryKey, "Key is null");
        final ByteBuffer pkBuf = primaryKey.toByteBuffer();
        final Txn<ByteBuffer> txn = tx.txn();
        if (indexFunctions.isEmpty()) {
            mainDb.delete(txn, pkBuf);
        } else {
            final ByteBuffer bb = mainDb.get(txn, pkBuf);
            if (bb != null) {
                // TODO probably avoid deserialization, just store the
                //  index keys next to the serialized value
                final T value = coder.fromBytes(bb);
                mainDb.delete(txn, pkBuf);
                indexFunctions.forEach((n, idxFun) -> {
                    final Key indexKey = idxFun.apply(value);
                    checkNotNull(indexKey, "Index key for value + " + value + " is null.");
                    indexes.get(n).delete(txn, indexKey.toByteBuffer(), pkBuf);
                });
            }
        }
    }

    protected void verifyKey(Key k) {
        checkNotNull(k, "Key is null");
    }

    void checkKeyAndValue(Key primaryKey, T value) {
        verifyKey(primaryKey);
        checkNotNull(value, "Value is null");
    }

    public List<T> getAll() {
        try(Tx.Read<ByteBuffer> tx = readTx()) {
            final List<T> result = new ArrayList<>();
            final CursorIterator<ByteBuffer> ci = mainDb.iterate(tx.txn());
            while (ci.hasNext()) {
                result.add(coder.fromBytes(ci.next().val()));
            }
            return result;
        }
    }

    public void clear() {
        try(Tx.Write<ByteBuffer> tx = writeTx()) {
            mainDb.drop(tx.txn());
            dropIndexes(tx);
        }
    }
}
