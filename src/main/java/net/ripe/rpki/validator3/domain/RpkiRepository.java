package net.ripe.rpki.validator3.domain;

import com.google.common.base.Objects;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import net.ripe.rpki.validator3.domain.constraints.ValidLocationURI;

import javax.persistence.Basic;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.ManyToMany;
import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.math.BigInteger;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Entity
@ToString
public class RpkiRepository extends AbstractEntity {

    public static final String TYPE = "rpki-repository";

    public enum Type {
        RRDP, RSYNC, RSYNC_PREFETCH
    }

    public enum Status {
        PENDING, FAILED, DOWNLOADED
    }

    @Basic(optional = false)
    @Enumerated(EnumType.STRING)
    @NotNull
    @Getter
    @Setter
    private Type type;

    @Basic(optional = false)
    @Enumerated(EnumType.STRING)
    @NotNull
    @Getter
    private Status status;

    @Basic
    @Getter
    private Instant lastDownloadedAt;

    @ManyToMany
    @JoinTable(joinColumns = @JoinColumn(name = "rpki_repository_id"), inverseJoinColumns = @JoinColumn(name = "trust_anchor_id"))
    @NotNull
    @NotEmpty
    @Getter
    private Set<TrustAnchor> trustAnchors = new HashSet<>();

    @Basic
    @ValidLocationURI
    @Getter
    private String rsyncRepositoryUri;

    @Basic
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

    protected RpkiRepository() {
    }

    public RpkiRepository(@NotNull @Valid TrustAnchor trustAnchor, @NotNull @ValidLocationURI String location, Type type) {
        addTrustAnchor(trustAnchor);
        this.status = Status.PENDING;
        switch (type) {
            case RRDP:
                this.type = Type.RRDP;
                this.rrdpNotifyUri = location;
                break;
            case RSYNC: case RSYNC_PREFETCH:
                this.type = type;
                this.rsyncRepositoryUri = location;
                break;
        }
    }

    public @ValidLocationURI @NotNull String getLocationUri() {
        return Objects.firstNonNull(rrdpNotifyUri, rsyncRepositoryUri);
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

    public boolean isDownloaded() {
        return status == Status.DOWNLOADED;
    }

    public void setFailed() {
        setFailed(Instant.now());
    }

    public void setFailed(Instant lastDownloadedAt) {
        this.status = Status.FAILED;
        this.lastDownloadedAt = lastDownloadedAt;
    }


    public void setDownloaded() {
        setDownloaded(Instant.now());
    }

    public void setDownloaded(Instant lastDownloadedAt) {
        this.status = Status.DOWNLOADED;
        this.lastDownloadedAt = lastDownloadedAt;
    }
}
