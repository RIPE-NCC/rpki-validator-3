package net.ripe.rpki.validator3.domain;

import lombok.Getter;
import net.ripe.rpki.validator3.domain.constraints.ValidLocationURI;

import javax.persistence.*;
import javax.validation.constraints.NotNull;
import java.util.Objects;

/**
 * Represents the a single run of validating a single trust anchor and all it's child CAs and related RPKI objects.
 */
@Entity
public class ValidationRun extends AbstractEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false, cascade = {CascadeType.PERSIST})
    @Getter
    private TrustAnchor trustAnchor;

    @Column(name = "trust_anchor_certificate_uri")
    @NotNull
    @ValidLocationURI
    @Getter
    private String trustAnchorCertificateURI;

    protected ValidationRun() {
    }

    public ValidationRun(TrustAnchor trustAnchor) {
        this.trustAnchor = Objects.requireNonNull(trustAnchor, "trust anchor is required");
        this.trustAnchorCertificateURI = trustAnchor.getLocations().get(0);
    }
}
