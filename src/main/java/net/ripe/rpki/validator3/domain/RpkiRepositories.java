package net.ripe.rpki.validator3.domain;

import net.ripe.rpki.validator3.domain.constraints.ValidLocationURI;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.List;
import java.util.Optional;

public interface RpkiRepositories {
    void register(@NotNull @Valid TrustAnchor trustAnchor, @NotNull @ValidLocationURI String uri);

    Optional<RpkiRepository> findByURI(@NotNull @ValidLocationURI String uri);

    RpkiRepository get(long id);

    List<RpkiRepository> findAll();
}
