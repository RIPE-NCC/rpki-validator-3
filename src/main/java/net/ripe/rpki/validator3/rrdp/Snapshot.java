package net.ripe.rpki.validator3.rrdp;

import java.util.Map;

public class Snapshot {
    private Map<String, RepoObject> objects;

    public Snapshot(Map<String, RepoObject> objects) {
        this.objects = objects;
    }
}
