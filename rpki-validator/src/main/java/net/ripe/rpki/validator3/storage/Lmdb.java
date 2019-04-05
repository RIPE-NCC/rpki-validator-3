package net.ripe.rpki.validator3.storage;

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
            return r;
        } finally {
            tx.close();
        }
    }

    public void writeTx0(Consumer<Tx.Write> c) {
        Tx.Write tx = writeTx();
        try {
            c.accept(tx);
            tx.txn().commit();
        } finally {
            tx.close();
        }
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
        Tx.Read tx = readTx();
        try {
            c.accept(tx);
        } finally {
            tx.close();
        }
    }

    public abstract Env<ByteBuffer> getEnv();
}
