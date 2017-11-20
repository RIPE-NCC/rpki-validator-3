package net.ripe.rpki.validator3.domain;

import java.util.List;
import java.util.Optional;

public interface ValidationRuns {
    void add(ValidationRun validationRun);

    void removeAllForTrustAnchor(TrustAnchor trustAnchor);

    <T extends ValidationRun> T get(Class<T> type, long id);

    <T extends ValidationRun> List<T> findAll(Class<T> type);

    <T extends ValidationRun> List<T> findLatestSuccessful(Class<T> type);

    Optional<TrustAnchorValidationRun> findLatestCompletedForTrustAnchor(TrustAnchor trustAnchor);

    void runCertificateTreeValidation(TrustAnchor trustAnchor);

    void removeAllForRpkiRepository(RpkiRepository repository);
}
