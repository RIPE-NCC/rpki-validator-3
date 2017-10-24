package net.ripe.rpki.validator3.rrdp;

import java.util.Collections;
import java.util.Map;

public class RepoObjects<T> {
    private Map<String, T> objects;

    public RepoObjects(Map<String, T> objects) {
        this.objects = objects;
    }

    public Map<String, T> asMap() {
        return Collections.unmodifiableMap(objects);
    }
}
