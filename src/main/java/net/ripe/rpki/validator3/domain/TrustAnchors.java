package net.ripe.rpki.validator3.domain;

import java.util.List;
import java.util.Optional;

public interface TrustAnchors {
    void add(TrustAnchor trustAnchor);

    void remove(TrustAnchor trustAnchor);

    TrustAnchor get(long id);

    List<TrustAnchor> findAll();

    List<TrustAnchor> findByName(String name);

    Optional<TrustAnchor> findBySubjectPublicKeyInfo(String subjectPublicKeyInfo);
}
