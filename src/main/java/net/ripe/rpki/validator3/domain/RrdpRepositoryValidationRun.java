package net.ripe.rpki.validator3.domain;

import lombok.Getter;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.ManyToOne;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;

@Entity
@DiscriminatorValue("RR")
public class RrdpRepositoryValidationRun extends RpkiRepositoryValidationRun {
    @ManyToOne(optional = false)
    @NotNull
    @Valid
    @Getter
    private RpkiRepository rpkiRepository;

    protected RrdpRepositoryValidationRun() {
    }

    @Override
    public void visit(Visitor visitor) {
        visitor.accept(this);
    }

    public RrdpRepositoryValidationRun(RpkiRepository rpkiRepository) {
        this.rpkiRepository = rpkiRepository;
    }

}
