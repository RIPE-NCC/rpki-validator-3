package net.ripe.rpki.validator3.storage.lmdb;

import com.google.common.collect.ImmutableList;
import net.ripe.rpki.validator3.storage.Coder;
import org.lmdbjava.Cursor;
import org.lmdbjava.CursorIterator;
import org.lmdbjava.Dbi;
import org.lmdbjava.DbiFlags;
import org.lmdbjava.Env;
import org.lmdbjava.KeyRange;
import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static org.lmdbjava.DbiFlags.MDB_CREATE;
import static org.lmdbjava.DbiFlags.MDB_DUPSORT;
import static org.lmdbjava.KeyRangeType.FORWARD_OPEN_CLOSED;

public class MultIxMap<T> extends IxBase<T> {

    public MultIxMap(final Env<ByteBuffer> env,
                     final String name,
                     final Coder<T> coder,
                     final Map<String, Function<T, Key>> indexFunctions) {
        super(env, name, coder, indexFunctions);
    }

    protected DbiFlags[] getMainDbCreateFlags() {
        return new DbiFlags[] { MDB_CREATE, MDB_DUPSORT };
    }

    protected DbiFlags[] getIndexDbiFlags() {
        return new DbiFlags[] { MDB_CREATE, MDB_DUPSORT };
    }

    public Map<Key, T> get(Key primaryKey) {
        try (Tx.Read<ByteBuffer> tx = readTx()) {
            return get(tx, primaryKey);
        }
    }

    public Map<Key, T> get(Tx.Read<ByteBuffer> txn, Key primaryKey) {
        verifyKey(primaryKey);
        final CursorIterator<ByteBuffer> iterate = mainDb.iterate(txn.txn(), new KeyRange<>(FORWARD_OPEN_CLOSED, primaryKey.toByteBuffer(), primaryKey.toByteBuffer()));
        final Map<Key, T> result = new HashMap<>();
        while (iterate.hasNext()) {
            final CursorIterator.KeyVal<ByteBuffer> next = iterate.next();
            result.put(new Key(next.key()), coder.fromBytes(next.val()));
        }
        return result;
    }

    public void put(Tx.Write<ByteBuffer> tx, Key primaryKey, T value) {
        final ByteBuffer pkBuf = primaryKey.toByteBuffer();
        final Txn<ByteBuffer> txn = tx.txn();
        mainDb.put(txn, pkBuf, coder.toBytes(value));
        indexFunctions.forEach((n, idxFun) -> {
            final Key indexKey = idxFun.apply(value);
            checkNotNull(indexKey, "Index key for value + " + value + " is null.");
            final Dbi<ByteBuffer> index = indexes.get(n);
            try (Cursor<ByteBuffer> c = index.openCursor(txn)) {
                c.put(indexKey.toByteBuffer(), pkBuf);
            }
        });
    }
}
