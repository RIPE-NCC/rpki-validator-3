package net.ripe.rpki.validator3.domain;

import java.util.List;

public interface TrustAnchorRepository {
    TrustAnchor get(long id);

    List<TrustAnchor> findAll();

    List<TrustAnchor> findByName(String name);
}
