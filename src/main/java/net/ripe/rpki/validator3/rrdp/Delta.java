package net.ripe.rpki.validator3.rrdp;

import java.util.Map;

public class Delta extends RepoObjects<DeltaElement> {

    public Delta(Map<String, DeltaElement> objects) {
        super(objects);
    }
}
