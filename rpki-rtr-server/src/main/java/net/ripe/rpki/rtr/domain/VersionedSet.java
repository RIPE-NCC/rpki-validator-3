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

import com.google.common.base.Objects;
import com.google.common.collect.Sets;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

public class VersionedSet<T> {

    private final Delta EMPTY_DELTA = new Delta(Collections.emptySortedSet(), Collections.emptySortedSet());

    private final Comparator<T> comparator;

    @Getter
    private long currentVersion;

    @Getter
    private SortedSet<T> values = Collections.emptySortedSet();

    private SortedMap<Long, Delta> deltas = Collections.emptySortedMap();

    public VersionedSet() {
        this(null, 0L);
    }

    public VersionedSet(Comparator<T> comparator, long initialVersion) {
        this.comparator = comparator;
        this.currentVersion = initialVersion;
    }

    public boolean updateValues(Collection<T> newValues) {
        SortedSet<T> updated = Collections.unmodifiableSortedSet(newSortedSet(newValues));
        if (values.equals(updated)) {
            return false;
        }

        Delta delta = calculateDelta(values, updated);
        SortedMap<Long, Delta> updatedDeltas = updatePreviousDeltas(delta, deltas);
        updatedDeltas.put(currentVersion, delta);

        ++currentVersion;
        values = updated;
        deltas = Collections.unmodifiableSortedMap(updatedDeltas);
        return true;
    }

    public long getOldestVersion() {
        return Objects.firstNonNull(deltas.firstKey(), currentVersion);
    }

    public void forgetDeltasBefore(long version) {
        deltas = Collections.unmodifiableSortedMap(new TreeMap<>(deltas.tailMap(version)));
    }

    public Optional<Delta> getDelta(long version) {
        if (version > currentVersion) {
            throw new IllegalArgumentException(String.format(
                "requested version %d must not be greater than current version %d",
                version,
                currentVersion
            ));
        } else if (version == currentVersion) {
            return Optional.of(EMPTY_DELTA);
        } else {
            return Optional.ofNullable(deltas.get(version));
        }
    }

    public int size() {
        return values.size();
    }

    @EqualsAndHashCode
    @ToString
    public class Delta {
        @Getter
        private final SortedSet<T> additions;
        @Getter
        private final SortedSet<T> removals;

        public Delta(SortedSet<T> additions, SortedSet<T> removals) {
            this.additions = Collections.unmodifiableSortedSet(additions);
            this.removals = Collections.unmodifiableSortedSet(removals);
        }

        public Delta append(Delta that) {
            // (this.additions - that.removals) union (that.additions - this.removals)
            SortedSet<T> additions = newSortedSet(this.additions);
            additions.removeAll(that.removals);
            additions.addAll(that.additions);
            additions.removeAll(this.removals);

            // (this.removals - that.additions) union (that.removals - this.additions)
            SortedSet<T> removals = newSortedSet(this.removals);
            removals.removeAll(that.additions);
            removals.addAll(that.removals);
            removals.removeAll(this.additions);

            return new Delta(
                additions,
                removals
            );
        }
    }
    private SortedMap<Long, Delta> updatePreviousDeltas(Delta delta, SortedMap<Long, Delta> previousDeltas) {
        return previousDeltas.entrySet().stream().collect(Collectors.toMap(
            entry -> entry.getKey(),
            entry -> entry.getValue().append(delta),
            // See Collectors#throwingMerger, which is private so we duplicate it here
            (u, v) -> {
                throw new IllegalStateException(String.format("Duplicate key %s", u));
            },
            TreeMap::new
        ));
    }


    private SortedSet<T> newSortedSet(Collection<? extends T> values) {
        TreeSet<T> result = new TreeSet<>(comparator);
        result.addAll(values);
        return result;
    }

    private Delta calculateDelta(Set<T> values, Set<T> updated) {
        SortedSet<T> v = new TreeSet<>(comparator);
        v.addAll(values);
        return new Delta(
            newSortedSet(Sets.difference(updated, values)),
            newSortedSet(Sets.difference(values, updated))
        );
    }
}
