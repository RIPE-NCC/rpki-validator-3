package net.ripe.rpki.validator3.domain;

import lombok.Getter;

import javax.persistence.*;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.HashSet;
import java.util.Set;

@Entity
@DiscriminatorValue("CT")
public class CertificateTreeValidationRun extends ValidationRun {
    public static final String TYPE = "certificate-tree-validation-run";

    @ManyToOne(optional = false)
    @NotNull
    @Valid
    @Getter
    private TrustAnchor trustAnchor;

    @ManyToMany
    @JoinTable(
        joinColumns = @JoinColumn(name = "validation_run_id"),
        inverseJoinColumns = @JoinColumn(name = "rpki_object_id")
    )
    @NotNull
    @Getter
    private Set<RpkiObject> validatedObjects = new HashSet<>();

    protected CertificateTreeValidationRun() {
    }

    public CertificateTreeValidationRun(TrustAnchor trustAnchor) {
        this.trustAnchor = trustAnchor;
    }

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public void visit(Visitor visitor) {
        visitor.accept(this);
    }
}
