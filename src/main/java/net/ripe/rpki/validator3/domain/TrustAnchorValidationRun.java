package net.ripe.rpki.validator3.domain;

import lombok.Getter;

import javax.persistence.*;
import javax.validation.constraints.NotNull;

@Entity
@DiscriminatorValue("TA")
public class TrustAnchorValidationRun extends ValidationRun {
    public static final String TYPE = "trust-anchor-validation-run";

    @ManyToOne(fetch = FetchType.LAZY, optional = false, cascade = {CascadeType.PERSIST})
    @Getter
    private TrustAnchor trustAnchor;

    @Column(name = "trust_anchor_certificate_uri")
    @NotNull
    @Getter
    private String trustAnchorCertificateURI;

    protected TrustAnchorValidationRun() {
    }

    public TrustAnchorValidationRun(TrustAnchor trustAnchor) {
        this.trustAnchor = trustAnchor;
        this.trustAnchorCertificateURI = trustAnchor.getLocations().get(0);
    }

    @Override
    public String getType() {
        return TYPE;
    }
}
