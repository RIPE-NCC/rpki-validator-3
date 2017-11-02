package net.ripe.rpki.validator3.domain;

import java.util.List;
import java.util.Optional;

public interface RpkiObjects {
    void add(RpkiObject rpkiObject);

    void remove(RpkiObject o);

    RpkiObject get(long id);

    Optional<RpkiObject> findBySha256(byte[] sha256);

    List<RpkiObject> all();

    Optional<RpkiObject> findLatestByTypeAndAuthorityKeyIdentifier(RpkiObject.Type type, byte[] authorityKeyIdentifier);

    List<RpkiObject> findCurrentlyValidated(RpkiObject.Type type);

    void merge(RpkiObject object);
}
