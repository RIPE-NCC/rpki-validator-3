package net.ripe.rpki.validator3.rrdp;

import java.math.BigInteger;

public class Delta {
    public final String uri;
    public final String hash;
    public final BigInteger serial;

    public Delta(String uri, String hash, BigInteger serial) {
        this.uri = uri;
        this.hash = hash;
        this.serial = serial;
    }
}
