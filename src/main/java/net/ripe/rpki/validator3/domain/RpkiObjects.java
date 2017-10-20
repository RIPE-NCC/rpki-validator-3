package net.ripe.rpki.validator3.domain;

import java.util.List;
import java.util.Optional;

public interface RpkiObjects {
    void add(RpkiObject rpkiObject);

    RpkiObject get(long id);

    Optional<RpkiObject> findBySha256(byte[] sha256);

    List<RpkiObject> all();
}
