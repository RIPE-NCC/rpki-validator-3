package net.ripe.rpki.validator3.domain;

import org.apache.commons.lang3.tuple.Pair;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public interface RpkiObjects {
    void add(RpkiObject rpkiObject);

    void remove(RpkiObject o);

    Stream<Pair<CertificateTreeValidationRun, RpkiObject>> findCurrentlyValidated();

    void merge(RpkiObject object);
    
    RpkiObject get(long id);

    Optional<RpkiObject> findBySha256(byte[] sha256);

    Stream<RpkiObject> all();

    Optional<RpkiObject> findLatestByTypeAndAuthorityKeyIdentifier(RpkiObject.Type type, byte[] authorityKeyIdentifier);

    Stream<Pair<CertificateTreeValidationRun, RpkiObject>> findCurrentlyValidated(RpkiObject.Type type);
}
