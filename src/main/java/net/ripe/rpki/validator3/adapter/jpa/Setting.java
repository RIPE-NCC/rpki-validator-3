package net.ripe.rpki.validator3.adapter.jpa;

import lombok.Getter;
import lombok.Setter;
import net.ripe.rpki.validator3.domain.AbstractEntity;

import javax.persistence.Basic;
import javax.persistence.Entity;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

@Entity
public class Setting extends AbstractEntity {
    @Basic
    @Getter
    @NotNull
    @NotEmpty
    private String key;

    @Basic
    @Getter
    @Setter
    @NotNull
    private String value;

    protected Setting() {
    }

    public Setting(String key, String value) {
        this.key = key;
        this.value = value;
    }
}
