package net.ripe.rpki.validator3.storage.data;

import lombok.Data;

import java.time.Instant;

@Data
public class Base<T extends Base> {
    private Id<T> id;
    private Instant createdAt;
    private Instant updatedAt;
}
