package net.ripe.rpki.validator3.api.trustanchors;

import lombok.Value;
import net.ripe.rpki.validator3.domain.TrustAnchor;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.Links;

import java.util.List;

@Value(staticConstructor = "of")
class TrustAnchorResource {
    String type;
    long id;
    String name;
    List<String> locations;
    String subjectPublicKeyInfo;
    Links links;

    static TrustAnchorResource of(TrustAnchor trustAnchor, Link selfRel) {
        return of(
            "trust-anchor",
            trustAnchor.getId(),
            trustAnchor.getName(),
            trustAnchor.getLocations(),
            trustAnchor.getSubjectPublicKeyInfo(),
            new Links(selfRel)
        );
    }
}
