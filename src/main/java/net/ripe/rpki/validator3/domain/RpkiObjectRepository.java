package net.ripe.rpki.validator3.domain;

import java.util.Optional;

public interface RpkiObjectRepository {
    void add(RpkiObject rpkiObject);

    RpkiObject get(long id);

    Optional<RpkiObject> findBySha256(byte[] sha256);
}
