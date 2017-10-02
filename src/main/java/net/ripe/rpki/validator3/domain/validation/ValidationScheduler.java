package net.ripe.rpki.validator3.domain.validation;

import net.ripe.rpki.validator3.domain.TrustAnchor;

public interface ValidationScheduler {
    void addTrustAnchor(TrustAnchor trustAnchor);
}
