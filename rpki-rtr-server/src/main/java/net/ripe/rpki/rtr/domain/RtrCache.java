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

import fj.data.Either;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.Getter;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.rtr.util.Locks;
import org.apache.commons.lang3.tuple.Triple;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
@Slf4j
public class RtrCache {
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SerialNumber initialVersion;

    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    @Getter
    private volatile short sessionId;
    private volatile boolean ready;
    private VersionedSet<RtrDataUnit> data;

    @Autowired
    public RtrCache(MeterRegistry registry) {
        this(registry, SerialNumber.zero());
    }

    public RtrCache(MeterRegistry registry, SerialNumber initialVersion) {
        this.initialVersion = initialVersion;
        reset();

        // Init metrics
        Gauge.builder("validated_objects_count", () -> this.data.size())
            .description("Number of validated objects")
            .register(registry);
        Gauge.builder("validated_objects_ready", () -> this.ready ? 1 : 0)
            .description("Status of the cache")
            .register(registry);
        Gauge.builder("validated_objects_session_id", () -> this.sessionId)
            .description("Session id of the cache")
            .register(registry);
        Gauge.builder("validated_objects_serial", () -> this.getSerialNumber().getValue())
            .description("Serial of the cache")
            .register(registry);
    }

    public void reset() {
        Locks.locked(lock.writeLock(), () -> {
            this.ready = false;
            generateNewSessionId();
            this.data = new VersionedSet<>(initialVersion);
        });
    }

    private void generateNewSessionId() {
        short newSessionId;
        do {
            newSessionId = (short) RANDOM.nextInt();
        } while (this.sessionId == newSessionId);
        this.sessionId = newSessionId;
    }

    public Optional<SerialNumber> update(Collection<RtrDataUnit> updatedPdus) {
        return update(updatedPdus.stream());
    }

    public Optional<SerialNumber> update(Stream<RtrDataUnit> updatedPdus) {
        return Locks.locked(lock.writeLock(), () -> {
            ready = true;
            if (data.updateValues(updatedPdus)) {
                log.info(
                        "{} validated ROAs updated to serial number {} (delta with {} announcements, {} withdrawals)",
                        data.size(),
                        data.getCurrentVersion().getValue(),
                        data.getDelta(data.getCurrentVersion().previous()).map(x -> x.getAdditions().size()).orElse(0),
                        data.getDelta(data.getCurrentVersion().previous()).map(x -> x.getRemovals().size()).orElse(0)
                );
                return Optional.of(data.getCurrentVersion());
            } else {
                log.info("no updates to cached data");
                return Optional.empty();
            }
        });
    }

    public Content getCurrentContent() {
        return Locks.locked(lock.readLock(), this::getCurrentContentNoLock);
    }

    private Content getCurrentContentNoLock() {
        return Content.of(sessionId, getSerialNumber(), ready, data.getValues());
    }

    /**
     * Serial number uses <a href="https://tools.ietf.org/html/rfc1982">RFC1982 serial number arithmetic</a>.
     *
     * @return the current serial number of the RTR cache
     */
    public SerialNumber getSerialNumber() {
        return Locks.locked(lock.readLock(), () -> data.getCurrentVersion());
    }

    /**
     * Serial number uses <a href="https://tools.ietf.org/html/rfc1982">RFC1982 serial number arithmetic</a>.
     *
     * @return the delta from the requested <code>serialNumber</code> to the current state
     */
    Optional<Delta> getDeltaFrom(SerialNumber serialNumber) {
        return Locks.locked(lock.readLock(), () -> {
            if (!ready || serialNumber.isAfter(getSerialNumber())) {
                return Optional.empty();
            } else if (serialNumber.equals(getSerialNumber())) {
                return Optional.of(Delta.of(sessionId, serialNumber, Collections.emptySortedSet(), Collections.emptySortedSet()));
            } else {
                Optional<VersionedSet.Delta<RtrDataUnit>> delta = data.getDelta(serialNumber);
                return delta.map(d -> Delta.of(sessionId, getSerialNumber(), d.getAdditions(), d.getRemovals()));
            }
        });
    }

    public Either<Delta, Content> getDeltaOrContent(SerialNumber serialNumber) {
        return Locks.locked(lock.readLock(), () -> getDeltaFrom(serialNumber)
                .<Either<Delta, Content>>map(Either::left)
                .orElseGet(() -> Either.right(getCurrentContentNoLock())));
    }

    public Set<SerialNumber> forgetDeltasBefore(SerialNumber serialNumber) {
        return Locks.locked(lock.writeLock(), () -> data.forgetDeltasBefore(serialNumber));
    }

    public State getState() {
        final Triple<Content, VersionedSet<RtrDataUnit>, Short> t =
                Locks.locked(lock.readLock(), () -> Triple.of(getCurrentContentNoLock(), data, sessionId));

        final Content content = t.getLeft();
        final VersionedSet<RtrDataUnit> data = t.getMiddle();
        final Short sessionId = t.getRight();
        SortedMap<SerialNumber, Delta> deltas = data.getDeltas().entrySet().stream().map(entry ->
            Delta.of(sessionId, entry.getKey(), entry.getValue().getAdditions(), entry.getValue().getRemovals())
        ).collect(toSortedMap());
        return new State(content, deltas);
    }

    private Collector<Delta, ?, SortedMap<SerialNumber, Delta>> toSortedMap() {
        return Collectors.toMap(
            delta -> delta.getSerialNumber(),
            delta -> delta,
            (u, v) -> { throw new IllegalStateException(String.format("Duplicate key %s", u)); },
            TreeMap::new
        );
    }

    @Value
    public static class State {
        Content content;
        SortedMap<SerialNumber, Delta> deltas;
    }

    @Value(staticConstructor = "of")
    public static class Content {
        short sessionId;
        SerialNumber serialNumber;
        boolean ready;
        SortedSet<RtrDataUnit> announcements;
    }

    @Value(staticConstructor = "of")
    public static class Delta {
        short sessionId;
        SerialNumber serialNumber;
        SortedSet<RtrDataUnit> announcements;
        SortedSet<RtrDataUnit> withdrawals;
    }
}
