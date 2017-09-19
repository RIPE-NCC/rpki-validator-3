package net.ripe.rpki.validator3.api.trustanchors;

import lombok.Value;

import java.net.URI;
import java.util.Optional;
import java.util.UUID;

@Value(staticConstructor = "of")
class TrustAnchorInfo {
    UUID id;
    String name;
    URI certificateLocation;
    String publicKeyInfo;
    Optional<URI> prefetchURI;
    Optional<byte[]> taCertificate;
}
