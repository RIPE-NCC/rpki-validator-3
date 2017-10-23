package net.ripe.rpki.validator3.domain;

import lombok.Getter;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import javax.persistence.*;
import javax.validation.constraints.NotNull;
import java.time.Instant;

@MappedSuperclass
public abstract class AbstractEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    @Getter
    private long id = -1;

    @SuppressWarnings("unused")
    @Version
    private Integer version;

    @Basic
    @NotNull
    @Getter
    private Instant createdAt;

    @Basic
    @NotNull
    @Getter
    private Instant updatedAt;

    protected AbstractEntity() {
        this.createdAt = this.updatedAt = Instant.now();
    }

    @PrePersist
    protected void prePersist() {
        this.updatedAt = Instant.now();
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE).append("id", id).toString();
    }
}
