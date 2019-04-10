package net.ripe.rpki.validator3.storage;

import net.ripe.rpki.validator3.storage.encoding.BsonCoder;
import net.ripe.rpki.validator3.storage.encoding.Coder;
import net.ripe.rpki.validator3.storage.encoding.FSTCoder;
import net.ripe.rpki.validator3.storage.encoding.GsonCoder;
import net.ripe.rpki.validator3.storage.lmdb.Tx;
import org.lmdbjava.Env;

import java.nio.ByteBuffer;
import java.util.function.Consumer;
import java.util.function.Function;

public abstract class Lmdb {

    public <T> T writeTx(Function<Tx.Write, T> f) {
        Tx.Write tx = Tx.write(getEnv());
        try {
            final T result = f.apply(tx);
            tx.txn().commit();
            if (tx.getOnCommit() != null) {
                tx.getOnCommit().forEach(r -> {
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
        Tx.Read tx = Tx.read(getEnv());
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
    }

    public <T> Coder<T> defaultCoder(Class<T> c) {
        return new GsonCoder<>(c);
    }


    public String status() {
        return getEnv().stat().toString();
    }
}
