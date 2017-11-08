package net.ripe.rpki.validator3.domain;

import lombok.Getter;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.ManyToMany;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.HashSet;
import java.util.Set;

@Entity
@DiscriminatorValue("RS")
public class RsyncRepositoryValidationRun extends RpkiRepositoryValidationRun {
    @ManyToMany
    @JoinTable(
        joinColumns = @JoinColumn(name = "validation_run_id"),
        inverseJoinColumns = @JoinColumn(name = "rpki_repository_id")
    )
    @NotNull
    @Valid
    @Getter
    private Set<RpkiRepository> rpkiRepositories = new HashSet<>();

    public RsyncRepositoryValidationRun() {
    }
}
