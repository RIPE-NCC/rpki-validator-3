package net.ripe.rpki.validator3.storage.data.coders;

import java.util.HashSet;
import java.util.Set;

public class Tags {
    private final Set<Short> uniqueTags = new HashSet<>();

    public synchronized short unique(int t) {
        final short tag = (short) t;
        if (uniqueTags.contains(tag)) {
            throw new RuntimeException("Tag " + tag + " is not unique.");
        }
        uniqueTags.add(tag);
        return tag;
    }
}
