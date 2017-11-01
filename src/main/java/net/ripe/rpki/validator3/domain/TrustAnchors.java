package net.ripe.rpki.validator3.domain;

import javax.validation.Valid;
import java.util.List;

public interface TrustAnchors {
    void add(@Valid TrustAnchor trustAnchor);

    void remove(TrustAnchor trustAnchor);

    TrustAnchor get(long id);

    List<TrustAnchor> findAll();

    List<TrustAnchor> findByName(String name);
}
