package net.ripe.rpki.validator3.domain;

import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ValidationRuns {
    void add(ValidationRun validationRun);

    void removeAllForTrustAnchor(TrustAnchor trustAnchor);

    ValidationRun get(long id);

    List<ValidationRun> findAll();

    Optional<ValidationRun> findLatestCompletedForTrustAnchor(TrustAnchor trustAnchor);
}
