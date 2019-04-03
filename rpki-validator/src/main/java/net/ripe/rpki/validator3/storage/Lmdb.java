package net.ripe.rpki.validator3.storage;

import net.ripe.rpki.validator3.storage.lmdb.Tx;
import org.lmdbjava.Env;

import java.nio.ByteBuffer;

public abstract class Lmdb {

    public Tx.Read readTx() {
        return Tx.read(getEnv());
    }

    public Tx.Write writeTx() {
        return Tx.write(getEnv());
    }

    public abstract Env<ByteBuffer> getEnv();
}
