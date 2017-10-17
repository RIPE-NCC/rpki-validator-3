package net.ripe.rpki.validator3.domain;

import lombok.Getter;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.ManyToOne;
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

    protected RpkiRepositoryValidationRun() {
    }

    public RpkiRepositoryValidationRun(RpkiRepository rpkiRepository) {
        super(rpkiRepository.getTrustAnchor());

        this.rpkiRepository = rpkiRepository;
    }

    @Override
    public String getType() {
        return TYPE;
    }
}
