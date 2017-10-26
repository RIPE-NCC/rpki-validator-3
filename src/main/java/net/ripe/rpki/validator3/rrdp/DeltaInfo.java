package net.ripe.rpki.validator3.rrdp;

import lombok.ToString;

import java.math.BigInteger;

@ToString
public class DeltaInfo {

    private final String uri;
    private final String hash;
    private final BigInteger serial;

    public DeltaInfo(String uri, String hash, BigInteger serial) {
        this.uri = uri;
        this.hash = hash;
        this.serial = serial;
    }

    public String getUri() {
        return uri;
    }

    public String getHash() {
        return hash;
    }

    public BigInteger getSerial() {
        return serial;
    }
}
