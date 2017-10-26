package net.ripe.rpki.validator3.rrdp;

import java.math.BigInteger;
import java.util.Map;

public class Delta extends RepoObjects<DeltaElement> {

    private final String sessionId;
    private final BigInteger serial;

    public Delta(Map<String, DeltaElement> objects, String sessionId, BigInteger serial) {
        super(objects);
        this.sessionId = sessionId;
        this.serial = serial;
    }

    public String getSessionId() {
        return sessionId;
    }

    public BigInteger getSerial() {
        return serial;
    }
}
