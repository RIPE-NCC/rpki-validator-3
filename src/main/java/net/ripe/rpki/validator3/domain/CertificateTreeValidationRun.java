package net.ripe.rpki.validator3.domain;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.ManyToOne;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;

@Entity
@DiscriminatorValue("CT")
public class CertificateTreeValidationRun extends ValidationRun {
    public static final String TYPE = "certificate-tree-validation-run";

    @ManyToOne(optional = false)
    @NotNull
    @Valid
    private TrustAnchor trustAnchor;

    protected CertificateTreeValidationRun() {
    }

    public CertificateTreeValidationRun(TrustAnchor trustAnchor) {
        this.trustAnchor = trustAnchor;
    }

    @Override
    public String getType() {
        return TYPE;
    }
}
