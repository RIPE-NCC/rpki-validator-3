package net.ripe.rpki.validator3.domain;

import lombok.Getter;
import lombok.ToString;
import net.ripe.rpki.validator3.domain.constraints.ValidLocationURI;

import javax.persistence.Basic;
import javax.persistence.Entity;
import javax.persistence.ManyToOne;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;

@Entity
@ToString
public class RpkiRepository extends AbstractEntity {

    public static final String TYPE = "rpki-repository";

    @ManyToOne(optional = false)
    @NotNull
    @Getter
    private TrustAnchor trustAnchor;

    @Basic(optional = false)
    @NotNull
    @ValidLocationURI
    @Getter
    private String uri;

    protected RpkiRepository() {
    }

    public RpkiRepository(@NotNull @Valid TrustAnchor trustAnchor, @NotNull @ValidLocationURI String uri) {
        this.trustAnchor = trustAnchor;
        this.uri = uri;
    }
}
