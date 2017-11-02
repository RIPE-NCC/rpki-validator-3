package net.ripe.rpki.validator3.domain;

import lombok.Getter;

import javax.persistence.*;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.HashSet;
import java.util.Set;

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


    @ManyToMany(cascade = {CascadeType.ALL})
    @JoinTable(
            name = "validation_run_validated_objects",
            joinColumns = {@JoinColumn(name = "validation_run_id")},
            inverseJoinColumns = {@JoinColumn(name = "rpki_object_id")}
    )
    private Set<RpkiObject> rpkiObjects = new HashSet<>();

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

    public Set<RpkiObject> getRpkiObjects() {
        return rpkiObjects;
    }

    public void addRpkiObject(RpkiObject rpkiObject) {
        rpkiObjects.add(rpkiObject);
    }

}
