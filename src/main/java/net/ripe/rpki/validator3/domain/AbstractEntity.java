package net.ripe.rpki.validator3.domain;

import lombok.Getter;

import javax.persistence.*;
import javax.validation.constraints.NotNull;
import java.time.Instant;

@MappedSuperclass
public abstract class AbstractEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    @Getter
    private long id = -1;

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
        this.createdAt = Instant.now();
    }

    @PrePersist
    private void prePersist() {
        this.updatedAt = Instant.now();
    }
}
