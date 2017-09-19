package net.ripe.rpki.validator3.api.trustanchors;

import lombok.Value;

import java.util.List;

@Value(staticConstructor = "of")
class TrustAnchorListResult {
    List<TrustAnchorInfo> trustAnchors;
}
