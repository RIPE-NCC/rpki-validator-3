package net.ripe.rpki.validator3.domain;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.Basic;
import javax.persistence.Entity;

@Entity
public class IgnoreFilter extends AbstractEntity {

    // TODO one of prefix or asn has to be not empty

    @Basic
    @Getter
    @Setter
    private String prefix;

    @Basic
    @Getter
    @Setter
    private Long asn;

    @Basic
    @Getter
    @Setter
    private String comment;

    protected IgnoreFilter() {
    }


}
