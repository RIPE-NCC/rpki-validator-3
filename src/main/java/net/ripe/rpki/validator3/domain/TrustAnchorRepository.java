package net.ripe.rpki.validator3.domain;

import javax.persistence.EntityNotFoundException;
import javax.validation.Valid;
import java.util.List;

public interface TrustAnchorRepository {
    void add(@Valid TrustAnchor trustAnchor);

    TrustAnchor get(long id) throws EntityNotFoundException;

    List<TrustAnchor> findAll();

    List<TrustAnchor> findByName(String name);
}
