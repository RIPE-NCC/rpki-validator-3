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

    public enum Status {
        RUNNING,
        SUCCEEDED,
        FAILED
    }

    @ManyToOne(fetch = FetchType.LAZY, optional = false, cascade = {CascadeType.PERSIST})
    @Getter
    private TrustAnchor trustAnchor;

    @Column(name = "trust_anchor_certificate_uri")
    @NotNull
    @ValidLocationURI
    @Getter
    private String trustAnchorCertificateURI;

    @Enumerated(value = EnumType.STRING)
    @NotNull
    @Getter
    private Status status = Status.RUNNING;

    @Basic
    @Getter
    private String failureMessage;

    @SuppressWarnings("unused")
    public ValidationRun() {
        super();
    }

    public ValidationRun(TrustAnchor trustAnchor) {
        this();
        this.trustAnchor = Objects.requireNonNull(trustAnchor, "trust anchor is required");
        this.trustAnchorCertificateURI = trustAnchor.getLocations().get(0);
    }

    public void succeeded() {
        this.status = Status.SUCCEEDED;
    }

    public void failed(String failureMessage) {
        this.status = Status.FAILED;
        this.failureMessage = Objects.requireNonNull(failureMessage, "failure message is required");
    }
}
