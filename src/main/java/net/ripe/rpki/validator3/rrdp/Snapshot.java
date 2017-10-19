package net.ripe.rpki.validator3.rrdp;

import java.util.Collections;
import java.util.Map;

public class Snapshot {
    private Map<String, RepoObject> objects;

    public Snapshot(Map<String, RepoObject> objects) {
        this.objects = objects;
    }

    public Map<String, RepoObject> asMap() {
        return Collections.unmodifiableMap(objects);
    }
}
