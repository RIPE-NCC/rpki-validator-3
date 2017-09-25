package net.ripe.rpki.validator3.domain;

import org.springframework.validation.annotation.Validated;

import javax.validation.Valid;
import java.util.List;

@Validated
public interface TrustAnchorRepository {
    void add(@Valid TrustAnchor trustAnchor);

    List<TrustAnchor> findByName(String name);
}
