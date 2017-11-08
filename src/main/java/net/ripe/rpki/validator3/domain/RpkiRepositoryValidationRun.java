package net.ripe.rpki.validator3.domain;

import lombok.Getter;

import javax.persistence.Basic;
import javax.persistence.CascadeType;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.ManyToMany;
import java.util.HashSet;
import java.util.Set;

public abstract class RpkiRepositoryValidationRun extends ValidationRun {
    public final static String TYPE = "rpki-repository-validation-run";

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

    @Override
    public String getType() {
        return TYPE;
    }

    public void objectAdded() {
        ++this.addedObjectCount;
    }

    public void addRpkiObject(RpkiObject rpkiObject) {
        rpkiObjects.add(rpkiObject);
    }

}
