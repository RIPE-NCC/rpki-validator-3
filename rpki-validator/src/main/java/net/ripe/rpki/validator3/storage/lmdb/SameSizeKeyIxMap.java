package net.ripe.rpki.validator3.storage.lmdb;

import net.ripe.rpki.validator3.storage.Coder;
import org.lmdbjava.DbiFlags;
import org.lmdbjava.Env;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.function.Function;

import static org.lmdbjava.DbiFlags.MDB_CREATE;
import static org.lmdbjava.DbiFlags.MDB_DUPFIXED;
import static org.lmdbjava.DbiFlags.MDB_DUPSORT;

public class SameSizeKeyIxMap<T> extends IxMap<T> {

    private final int keySizeInBytes;

    public SameSizeKeyIxMap(int keySizeInBytes,
                            Env<ByteBuffer> env,
                            String name,
                            Coder<T> coder,
                            Map<String, Function<T, Key>> indexFunctions) {
        super(env, name, coder, indexFunctions);
        this.keySizeInBytes = keySizeInBytes;
    }

    @Override
    protected DbiFlags[] getIndexDbiFlags() {
        return new DbiFlags[] { MDB_CREATE, MDB_DUPSORT, MDB_DUPFIXED };
    }

    @Override
    protected void verifyKey(Key k) {
        super.verifyKey(k);
        if (k.size() != keySizeInBytes) {
            throw new IllegalArgumentException("Key size has to be " + keySizeInBytes + " but was " + k.size());
        }
    }
}
