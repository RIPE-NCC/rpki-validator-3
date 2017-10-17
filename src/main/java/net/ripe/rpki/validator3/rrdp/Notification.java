package net.ripe.rpki.validator3.rrdp;

import java.math.BigInteger;
import java.util.List;

public class Notification {

    public final String sessionId;
    public final BigInteger serial;
    public final String snapshotUrl;
    public final String snapshotHash;
    public final List<Delta> deltas;

    public Notification(String sessionId, BigInteger serial, String snapshotUrl, String snapshotHash, List<Delta> deltas) {
        this.sessionId = sessionId;
        this.serial = serial;
        this.snapshotUrl = snapshotUrl;
        this.snapshotHash = snapshotHash;
        this.deltas = deltas;
    }
}
