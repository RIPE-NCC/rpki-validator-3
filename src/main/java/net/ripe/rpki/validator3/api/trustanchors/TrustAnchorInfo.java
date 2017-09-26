package net.ripe.rpki.validator3.api.trustanchors;

import lombok.Value;
import net.ripe.rpki.validator3.domain.TrustAnchor;

import java.util.List;

@Value(staticConstructor = "of")
class TrustAnchorInfo {
    String type = "trust-anchor";
    long id;
    String name;
    List<String> locations;
    String subjectPublicKeyInfo;

    static TrustAnchorInfo of(TrustAnchor trustAnchor) {
        return of(
            trustAnchor.getId(),
            trustAnchor.getName(),
            trustAnchor.getLocations(),
            trustAnchor.getSubjectPublicKeyInfo()
        );
    }
}
