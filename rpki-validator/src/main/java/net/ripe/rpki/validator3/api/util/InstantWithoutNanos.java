/**
 * The BSD License
 *
 * Copyright (c) 2010-2018 RIPE NCC
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *   - Redistributions of source code must retain the above copyright notice,
 *     this list of conditions and the following disclaimer.
 *   - Redistributions in binary form must reproduce the above copyright notice,
 *     this list of conditions and the following disclaimer in the documentation
 *     and/or other materials provided with the distribution.
 *   - Neither the name of the RIPE NCC nor the names of its contributors may be
 *     used to endorse or promote products derived from this software without
 *     specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
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
