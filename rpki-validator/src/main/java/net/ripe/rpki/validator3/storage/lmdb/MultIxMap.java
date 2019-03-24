package net.ripe.rpki.validator3.storage.lmdb;

import net.ripe.rpki.validator3.storage.Coder;
import org.lmdbjava.CursorIterator;
import org.lmdbjava.DbiFlags;
import org.lmdbjava.Env;
import org.lmdbjava.KeyRange;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.lmdbjava.DbiFlags.MDB_CREATE;
import static org.lmdbjava.DbiFlags.MDB_DUPSORT;

public class MultIxMap<T> extends IxBase<T> {

    public MultIxMap(final Env<ByteBuffer> env,
                     final String name,
                     final Coder<T> coder) {
        super(env, name, coder);
    }

    protected DbiFlags[] getMainDbCreateFlags() {
        return new DbiFlags[]{MDB_CREATE, MDB_DUPSORT};
    }

    protected DbiFlags[] getIndexDbiFlags() {
        return new DbiFlags[]{MDB_CREATE, MDB_DUPSORT};
    }

    public List<T> get(Key primaryKey) {
        try (Tx.Read<ByteBuffer> tx = readTx()) {
            return get(tx, primaryKey);
        }
    }

    public List<T> get(Tx.Read<ByteBuffer> txn, Key primaryKey) {
        verifyKey(primaryKey);
        final ByteBuffer pkBuf = primaryKey.toByteBuffer();
        final CursorIterator<ByteBuffer> iterate = mainDb.iterate(txn.txn(), KeyRange.closed(pkBuf, pkBuf));
        final List<T> result = new ArrayList<>();
        while (iterate.hasNext()) {
            final CursorIterator.KeyVal<ByteBuffer> next = iterate.next();
            result.add(coder.fromBytes(next.val()));
        }
        return result;
    }

    public void put(Key primaryKey, T value) {
        checkKeyAndValue(primaryKey, value);
        Tx.use(writeTx(), tx -> put(tx, primaryKey, value));
    }

    public void put(Tx.Write<ByteBuffer> tx, Key primaryKey, T value) {
        mainDb.put(tx.txn(), primaryKey.toByteBuffer(), coder.toBytes(value));
    }

    public void delete(Key primaryKey) {
        verifyKey(primaryKey);
        Tx.use(writeTx(), tx -> delete(tx, primaryKey));
    }

    public void delete(Tx.Write<ByteBuffer> tx, Key primaryKey) {
        mainDb.delete(tx.txn(), primaryKey.toByteBuffer());
    }

    public void delete(Key primaryKey, T value) {
        verifyKey(primaryKey);
        Tx.use(writeTx(), tx -> delete(tx, primaryKey, value));
    }

    public void delete(Tx.Write<ByteBuffer> tx, Key primaryKey, T value) {
        mainDb.delete(tx.txn(), primaryKey.toByteBuffer(), coder.toBytes(value));
    }

    @Override
    public void clear() {
        Tx.use(writeTx(), tx -> mainDb.drop(tx.txn()));
    }
}
