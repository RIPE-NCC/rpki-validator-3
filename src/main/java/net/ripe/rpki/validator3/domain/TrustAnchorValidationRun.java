package net.ripe.rpki.validator3.domain;

import lombok.Getter;
import net.ripe.rpki.validator3.domain.constraints.ValidLocationURI;

import javax.persistence.Column;
import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.validation.constraints.NotNull;

@Entity
@DiscriminatorValue("TA")
public class TrustAnchorValidationRun extends ValidationRun {
    public static final String TYPE = "trust-anchor-validation-run";

    @Column(name = "trust_anchor_certificate_uri")
    @NotNull
    @ValidLocationURI
    @Getter
    private String trustAnchorCertificateURI;

    protected TrustAnchorValidationRun() {
    }

    public TrustAnchorValidationRun(TrustAnchor trustAnchor) {
        super(trustAnchor);
        this.trustAnchorCertificateURI = trustAnchor.getLocations().get(0);
    }

    @Override
    public String getType() {
        return TYPE;
    }

    public String getTrustAnchorCertificateURI() {
        return this.trustAnchorCertificateURI;
    }
}
