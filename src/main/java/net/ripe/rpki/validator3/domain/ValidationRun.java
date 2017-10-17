package net.ripe.rpki.validator3.domain;

import lombok.Getter;

import javax.persistence.*;
import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;


/**
 * Represents the a single run of validating a single trust anchor and all it's child CAs and related RPKI objects.
 */
@Entity
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "TYPE", columnDefinition = "CHAR(2)")
public abstract class ValidationRun extends AbstractEntity {

    public enum Status {
        RUNNING,
        SUCCEEDED,
        FAILED
    }

    @ManyToOne(fetch = FetchType.LAZY, optional = false, cascade = {CascadeType.PERSIST})
    @Getter
    private TrustAnchor trustAnchor;

    @Enumerated(value = EnumType.STRING)
    @NotNull
    @Getter
    private Status status = Status.RUNNING;

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "validationRun")
    @Getter
    private List<ValidationCheck> validationChecks = new ArrayList<>();

    @SuppressWarnings("unused")
    public ValidationRun() {
        super();
    }

    public ValidationRun(TrustAnchor trustAnchor) {
        this();
        this.trustAnchor = Objects.requireNonNull(trustAnchor, "trust anchor is required");
    }

    public abstract String getType();

    public void succeeded() {
        this.status = Status.SUCCEEDED;
    }

    public void failed() {
        this.status = Status.FAILED;
    }

    public void addCheck(ValidationCheck validationCheck) {
        this.validationChecks.add(validationCheck);
    }
}
