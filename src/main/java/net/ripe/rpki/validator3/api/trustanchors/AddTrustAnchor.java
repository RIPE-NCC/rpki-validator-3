package net.ripe.rpki.validator3.api.trustanchors;

import lombok.Value;

@Value(staticConstructor = "of")
class AddTrustAnchor {
    String type = "add-trust-anchor";
    String name;
    String certificate;
}
