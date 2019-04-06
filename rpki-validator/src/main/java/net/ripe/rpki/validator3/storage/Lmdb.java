package net.ripe.rpki.validator3.storage;

import net.ripe.rpki.validator3.storage.encoding.Coder;
import net.ripe.rpki.validator3.storage.encoding.FSTCoder;
import net.ripe.rpki.validator3.storage.lmdb.Tx;
import org.lmdbjava.Env;

import java.nio.ByteBuffer;
import java.util.function.Consumer;
import java.util.function.Function;

public abstract class Lmdb {

    public Tx.Read readTx() {
        return Tx.read(getEnv());
    }

    public Tx.Write writeTx() {
        return Tx.write(getEnv());
    }

    public <T> T writeTx(Function<Tx.Write, T> f) {
        Tx.Write tx = writeTx();
        try {
            final T r = f.apply(tx);
            tx.txn().commit();
            if (tx.getOnCommit() != null) {
                tx.getOnCommit().forEach(Runnable::run);
            }
            return r;
        } finally {
            tx.close();
        }
    }

    public void writeTx0(Consumer<Tx.Write> c) {
        writeTx(tx -> {
            c.accept(tx);
            return null;
        });
    }

    public <T> T readTx(Function<Tx.Read, T> f) {
        Tx.Read tx = readTx();
        try {
            return f.apply(tx);
        } finally {
            tx.close();
        }
    }

    public void readTx0(Consumer<Tx.Read> c) {
        readTx(tx -> {
            c.accept(tx);
            return null;
        });
    }

    public abstract Env<ByteBuffer> getEnv();

    public <T> Coder<T> defaultCoder() {
        return new FSTCoder<>();
//        return new BsonCoder<>();
    }
}
