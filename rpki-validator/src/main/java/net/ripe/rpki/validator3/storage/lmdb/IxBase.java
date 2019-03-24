package net.ripe.rpki.validator3.storage.lmdb;

import lombok.Getter;
import net.ripe.rpki.validator3.storage.Coder;
import org.lmdbjava.CursorIterator;
import org.lmdbjava.Dbi;
import org.lmdbjava.DbiFlags;
import org.lmdbjava.Env;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public abstract class IxBase<T> {
    private final Env<ByteBuffer> env;
    @Getter
    private final String name;

    protected final Dbi<ByteBuffer> mainDb;
    protected final Coder<T> coder;

    public IxBase(final Env<ByteBuffer> env,
                  final String name,
                  final Coder<T> coder) {
        this.env = env;
        this.name = name;
        this.coder = coder;
        this.mainDb = env.openDbi(name + ":main", getMainDbCreateFlags());
    }

    protected abstract DbiFlags[] getMainDbCreateFlags();

    protected abstract DbiFlags[] getIndexDbiFlags();

    protected static void checkNotNull(Object v, String s) {
        if (v == null) {
            throw new NullPointerException(s);
        }
    }

    public Tx.Read readTx() {
        return Tx.read(env);
    }

    public Tx.Write writeTx() {
        return Tx.write(env);
    }

    protected void verifyKey(Key k) {
        checkNotNull(k, "Key is null");
    }

    void checkKeyAndValue(Key primaryKey, T value) {
        verifyKey(primaryKey);
        checkNotNull(value, "Value is null");
    }

    public List<T> values(Tx.Read tx) {
        final List<T> result = new ArrayList<>();
        final CursorIterator<ByteBuffer> ci = mainDb.iterate(tx.txn());
        while (ci.hasNext()) {
            result.add(coder.fromBytes(ci.next().val()));
        }
        return result;
    }

    public abstract void clear();
}
