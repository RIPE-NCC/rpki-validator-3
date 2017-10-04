package net.ripe.rpki.validator3.domain;

import org.springframework.stereotype.Repository;

@Repository
public interface ValidationRunRepository {
    void add(ValidationRun validationRun);

    void removeAllForTrustAnchor(TrustAnchor trustAnchor);
}
