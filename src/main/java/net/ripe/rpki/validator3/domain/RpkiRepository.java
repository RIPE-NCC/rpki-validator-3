package net.ripe.rpki.validator3.domain;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import net.ripe.rpki.validator3.domain.constraints.ValidLocationURI;

import javax.persistence.*;
import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.math.BigInteger;
import java.util.HashSet;
import java.util.Set;

@Entity
@ToString
public class RpkiRepository extends AbstractEntity {

    public static final String TYPE = "rpki-repository";

    public enum Status {
        PENDING, FAILED, DOWNLOADED
    }

    @ManyToMany
    @JoinTable(joinColumns = @JoinColumn(name = "rpki_repository_id"), inverseJoinColumns = @JoinColumn(name = "trust_anchor_id"))
    @NotNull
    @NotEmpty
    @Getter
    private Set<TrustAnchor> trustAnchors = new HashSet<>();

    @Basic(optional = false)
    @NotNull
    @ValidLocationURI
    @Getter
    private String rrdpNotifyUri;

    @Basic
    @Getter
    @Setter
    private String rrdpSessionId;

    @Basic
    @Getter
    @Setter
    private BigInteger rrdpSerial;

    @Basic(optional = false)
    @Enumerated(EnumType.STRING)
    @NotNull
    @Getter
    private Status status;

    protected RpkiRepository() {
    }

    public RpkiRepository(@NotNull @Valid TrustAnchor trustAnchor, @NotNull @ValidLocationURI String uri) {
        addTrustAnchor(trustAnchor);
        this.rrdpNotifyUri = uri;
        this.status = Status.PENDING;
    }

    public void addTrustAnchor(@NotNull @Valid TrustAnchor trustAnchor) {
        this.trustAnchors.add(trustAnchor);
    }

    public boolean isPending() {
        return status == Status.PENDING;
    }

    public boolean isFailed() {
        return status == Status.FAILED;
    }

    public void setDownloaded() {
        this.status = Status.DOWNLOADED;
    }
}
