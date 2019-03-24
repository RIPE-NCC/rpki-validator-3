package net.ripe.rpki.validator3.storage.lmdb;

import net.ripe.rpki.validator3.storage.Lmdb;

public class LmdbTests {
    public static Lmdb makeLmdb(String path) throws Exception {
        return new Lmdb(path);
    }
}
