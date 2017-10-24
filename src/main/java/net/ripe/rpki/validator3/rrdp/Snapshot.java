package net.ripe.rpki.validator3.rrdp;

import java.util.Map;

public class Snapshot extends RepoObjects<SnapshotObject> {

    public Snapshot(Map<String, SnapshotObject> objects) {
        super(objects);
    }
}
