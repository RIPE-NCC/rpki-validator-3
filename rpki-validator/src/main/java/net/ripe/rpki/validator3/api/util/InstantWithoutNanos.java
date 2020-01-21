package net.ripe.rpki.validator3.api.util;

import lombok.EqualsAndHashCode;
import lombok.experimental.Delegate;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalAdjuster;
import java.time.temporal.TemporalAmount;

@EqualsAndHashCode
public class InstantWithoutNanos implements Temporal, TemporalAdjuster, Comparable<InstantWithoutNanos>, Serializable {
    @Delegate(excludes = SkippedDelegatedMethods.class)
    private final Instant instant;

    private InstantWithoutNanos(Instant instant) {
        this.instant = instant;
    }

    public static InstantWithoutNanos now() {
        return from(Instant.now());
    }

    public static InstantWithoutNanos from(Instant then) {
        return new InstantWithoutNanos(then.truncatedTo(ChronoUnit.MILLIS));
    }

    public static InstantWithoutNanos ofEpochMilli(long fromByteArray) {
        return from(Instant.ofEpochMilli(fromByteArray));
    }

    public boolean isBefore(InstantWithoutNanos then) {
        return instant.isBefore(then.instant);
    }

    public InstantWithoutNanos minus(TemporalAmount amountToSubtract) {
        return from(instant.minus(amountToSubtract));
    }

    @Override
    public int compareTo(@NotNull InstantWithoutNanos that) {
        return this.instant.compareTo(that.instant);
    }

    @Override
    public String toString() {
        return instant.toString();
    }
}

interface SkippedDelegatedMethods {
    public Instant minus(TemporalAmount amountToSubtract);
}
