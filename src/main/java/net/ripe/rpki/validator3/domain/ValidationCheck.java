package net.ripe.rpki.validator3.domain;

import lombok.Getter;
import net.ripe.rpki.commons.validation.ValidationStatus;

import javax.persistence.*;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Entity
public class ValidationCheck extends AbstractEntity {

    public enum Status {
        WARNING, ERROR
    }

    @ManyToOne(optional = false, cascade = CascadeType.PERSIST)
    @NotNull
    @Getter
    private ValidationRun validationRun;

    @Basic(optional = false)
    @NotEmpty
    @NotNull
    @Getter
    private String location;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    @NotNull
    @Getter
    private Status status;

    @Basic(optional = false)
    @NotEmpty
    @NotNull
    @Getter
    private String key;

    @ElementCollection(fetch = FetchType.EAGER)
    @OrderColumn
    @Getter
    @NotNull
    private List<String> parameters = new ArrayList<>();

    protected ValidationCheck() {
    }

    public ValidationCheck(ValidationRun validationRun, String location, net.ripe.rpki.commons.validation.ValidationCheck check) {
        this.validationRun = validationRun;
        this.location = location;
        this.status = mapStatus(check.getStatus());
        this.key = check.getKey();
        this.parameters = Arrays.asList(check.getParams());
    }

    public ValidationCheck(ValidationRun validationRun, String location, Status status, String key, String... parameters) {
        this.validationRun = validationRun;
        this.location = location;
        this.status = status;
        this.key = key;
        this.parameters.addAll(Arrays.asList(parameters));
    }

    static Status mapStatus(ValidationStatus status) {
        switch (status) {
            case PASSED:
                throw new IllegalArgumentException("PASSED checks should not be stored: " + status);
            case WARNING:
                return Status.WARNING;
            case ERROR: case FETCH_ERROR:
                return Status.ERROR;
        }
        throw new IllegalArgumentException("Unknown status: " + status);
    }

    public static ValidationCheck of(net.ripe.rpki.commons.validation.ValidationResult result) {

        return null;
    }

    @Override
    public String toString() {
        return toStringBuilder()
            .append("location", location)
            .append("status", status)
            .append("key", key)
            .append("parameters", parameters)
            .build();
    }
}
