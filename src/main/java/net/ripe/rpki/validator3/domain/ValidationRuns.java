package net.ripe.rpki.validator3.domain;

import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ValidationRuns {
    void add(ValidationRun validationRun);

    void removeAllForTrustAnchor(TrustAnchor trustAnchor);

    <T extends ValidationRun> T get(Class<T> type, long id);

    <T extends ValidationRun> List<T> findAll(Class<T> type);

    Optional<TrustAnchorValidationRun> findLatestCompletedForTrustAnchor(TrustAnchor trustAnchor);
}
