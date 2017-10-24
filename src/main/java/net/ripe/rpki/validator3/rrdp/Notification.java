package net.ripe.rpki.validator3.rrdp;

import java.math.BigInteger;
import java.util.List;

public class Notification {

    public final String sessionId;
    public final BigInteger serial;
    public final String snapshotUri;
    public final String snapshotHash;
    public final List<DeltaInfo> deltas;

    public Notification(String sessionId, BigInteger serial, String snapshotUri, String snapshotHash, List<DeltaInfo> deltas) {
        this.sessionId = sessionId;
        this.serial = serial;
        this.snapshotUri = snapshotUri;
        this.snapshotHash = snapshotHash;
        this.deltas = deltas;
    }
}
