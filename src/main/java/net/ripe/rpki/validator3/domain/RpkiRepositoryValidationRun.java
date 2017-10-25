package net.ripe.rpki.validator3.domain;

import lombok.Getter;

import javax.persistence.*;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;

@Entity
@DiscriminatorValue("RR")
public class RpkiRepositoryValidationRun extends ValidationRun {
    public final static String TYPE = "rpki-repository-validation-run";

    @ManyToOne(optional = false)
    @NotNull
    @Valid
    @Getter
    private RpkiRepository rpkiRepository;

    @Basic(optional = false)
    @Getter
    private int addedObjectCount;

    protected RpkiRepositoryValidationRun() {
    }

    public RpkiRepositoryValidationRun(RpkiRepository rpkiRepository) {
        this.rpkiRepository = rpkiRepository;
    }

    @Override
    public String getType() {
        return TYPE;
    }

    public void objectAdded() {
        ++this.addedObjectCount;
    }
}
