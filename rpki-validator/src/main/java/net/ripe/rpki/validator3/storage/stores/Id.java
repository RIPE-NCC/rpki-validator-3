package net.ripe.rpki.validator3.storage.stores;

import net.ripe.rpki.validator3.storage.lmdb.Key;

import java.math.BigInteger;

public class Id {
    public static Key key(long id) {
        return Key.of(BigInteger.valueOf(id));
    }
}
