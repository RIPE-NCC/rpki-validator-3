package net.ripe.rpki.validator3.domain;

import lombok.Getter;

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

    @ManyToOne(optional = true, cascade = CascadeType.PERSIST)
    @Getter
    private RpkiObject rpkiObject;

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

    public ValidationCheck(ValidationRun validationRun, RpkiObject rpkiObject, String location, net.ripe.rpki.commons.validation.ValidationCheck check) {
        this.validationRun = validationRun;
        this.rpkiObject = rpkiObject;
        this.location = location;

        switch (check.getStatus()) {
            case PASSED:
                throw new IllegalArgumentException("PASSED checks should not be stored: " + check);
            case WARNING:
                this.status = Status.WARNING;
                break;
            case ERROR: case FETCH_ERROR:
                this.status = Status.ERROR;
                break;
        }

        this.key = check.getKey();
        this.parameters = Arrays.asList(check.getParams());
    }

    public ValidationCheck(ValidationRun validationRun, String location, Status status, String key, String... parameters) {
        this.validationRun = validationRun;
        this.rpkiObject = null;
        this.location = location;
        this.status = status;
        this.key = key;
        this.parameters.addAll(Arrays.asList(parameters));
    }
}
