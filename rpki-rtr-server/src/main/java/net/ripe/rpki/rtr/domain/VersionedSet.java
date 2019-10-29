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
package net.ripe.rpki.rtr.domain;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class VersionedSet<T extends Comparable<T>> {

    private final Delta<T> EMPTY_DELTA = new Delta<T>(Collections.emptySortedSet(), Collections.emptySortedSet());

    @Getter
    private SerialNumber currentVersion;

    @Getter
    private SortedSet<T> values = Collections.emptySortedSet();

    @Getter
    private SortedMap<SerialNumber, Delta<T>> deltas = Collections.emptySortedMap();

    public VersionedSet() {
        this(SerialNumber.zero());
    }

    public VersionedSet(SerialNumber initialVersion) {
        this.currentVersion = initialVersion;
    }

    public boolean updateValues(Collection<T> newValues) {
        return updateValues(newValues.stream());
    }

    public boolean updateValues(Stream<T> newValues) {
        SortedSet<T> updatedValues = Collections.unmodifiableSortedSet(newSortedSet(newValues));
        if (values.equals(updatedValues)) {
            return false;
        }

        Delta<T> delta = Delta.calculate(values, updatedValues);
        deltas = updateDeltas(delta);

        currentVersion = currentVersion.next();
        values = updatedValues;
        return true;
    }

    public Set<SerialNumber> forgetDeltasBefore(SerialNumber version) {
        Set<SerialNumber> olderDeltas = deltas.keySet().stream().filter(key -> key.compareTo(version) < 0).collect(Collectors.toSet());
        if (!olderDeltas.isEmpty()) {
            deltas = Collections.unmodifiableSortedMap(new TreeMap<>(Maps.filterEntries(deltas, entry -> !olderDeltas.contains(entry.getKey()))));
        }
        return olderDeltas;
    }

    public Optional<Delta<T>> getDelta(SerialNumber version) {
        if (version.isAfter(currentVersion)) {
            throw new IllegalArgumentException(String.format(
                "requested version %d must not be greater than current version %d",
                version,
                currentVersion
            ));
        } else if (version.equals(currentVersion)) {
            return Optional.of(EMPTY_DELTA);
        } else {
            return Optional.ofNullable(deltas.get(version));
        }
    }

    public int size() {
        return values.size();
    }

    @EqualsAndHashCode
    @Getter
    @ToString
    public static class Delta<T>{
        private final SortedSet<T> additions;
        private final SortedSet<T> removals;

        private Delta(SortedSet<T> additions, SortedSet<T> removals) {
            this.additions = Collections.unmodifiableSortedSet(additions);
            this.removals = Collections.unmodifiableSortedSet(removals);
        }

        public static <T> Delta<T> of(SortedSet<T> additions, SortedSet<T> removals) {
            return new Delta<>(additions, removals);
        }

        public static <T> Delta<T> calculate(Set<T> values, Set<T> updated) {
            return of(
                newSortedSet(Sets.difference(updated, values)),
                newSortedSet(Sets.difference(values, updated))
            );
        }

        public Delta<T> append(Delta<T> that) {
            SortedSet<T> additions = newSortedSet(Sets.union(
                Sets.difference(this.additions, that.removals),
                Sets.difference(that.additions, this.removals)
            ));

            SortedSet<T> removals = newSortedSet(Sets.union(
                Sets.difference(this.removals, that.additions),
                Sets.difference(that.removals, this.additions)
            ));

            return new Delta<>(
                additions,
                removals
            );
        }
    }

    private SortedMap<SerialNumber, Delta<T>> updateDeltas(Delta<T> delta) {
        TreeMap<SerialNumber, Delta<T>> updatedDeltas = new TreeMap<>(deltas);
        updatedDeltas.replaceAll((key, value) -> value.append(delta));
        updatedDeltas.put(currentVersion, delta);
        return Collections.unmodifiableSortedMap(updatedDeltas);
    }

    private static <T> SortedSet<T> newSortedSet(Collection<? extends T> values) {
        return new TreeSet<>(values);
    }

    private static <T> SortedSet<T> newSortedSet(Stream<? extends T> values) {
        return values.collect(Collectors.toCollection(TreeSet::new));
    }
}
