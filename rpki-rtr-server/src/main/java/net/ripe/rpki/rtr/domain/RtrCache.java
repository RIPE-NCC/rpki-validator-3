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
import lombok.Getter;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;

@Component
@Slf4j
public class RtrCache {
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SerialNumber initialVersion;

    @Getter
    private volatile short sessionId;
    private volatile boolean ready;
    private VersionedSet<RtrDataUnit> data;

    public RtrCache() {
        this(SerialNumber.zero());
    }

    public RtrCache(SerialNumber initialVersion) {
        this.initialVersion = initialVersion;
        reset();
    }

    public synchronized void reset() {
        this.ready = false;

        short newSessionId;
        do {
            newSessionId = (short) RANDOM.nextInt();
        } while (this.sessionId == newSessionId);
        this.sessionId = newSessionId;

        this.data = new VersionedSet<>(initialVersion);
    }

    public synchronized Optional<SerialNumber> update(Collection<RtrDataUnit> updatedPdus) {
        ready = true;
        if (data.updateValues(updatedPdus)) {
            log.info(
                "{} validated ROAs updated to serial number {} (delta with {} announcements, {} withdrawals)",
                data.size(),
                data.getCurrentVersion().getValue(),
                data.getDelta(data.getCurrentVersion().previous()).map(x -> x.getAdditions().size()).orElse(0),
                data.getDelta(data.getCurrentVersion().previous()).map(x -> x.getRemovals().size()).orElse(0)
            );
            return Optional.of(getSerialNumber());
        } else {
            log.info("no updates to cached data");
            return Optional.empty();
        }
    }

    public synchronized Content getCurrentContent() {
        return Content.of(sessionId, getSerialNumber(), ready, data.getValues());
    }

    /**
     * Serial number uses <a href="https://tools.ietf.org/html/rfc1982">RFC1982 serial number arithmetic</a>.
     *
     * @return the current serial number of the RTR cache
     */
    public synchronized SerialNumber getSerialNumber() {
        return data.getCurrentVersion();
    }

    /**
     * Serial number uses <a href="https://tools.ietf.org/html/rfc1982">RFC1982 serial number arithmetic</a>.
     *
     * @return the delta from the requested <code>serialNumber</code> to the current state
     */
    public synchronized Optional<Delta> getDeltaFrom(SerialNumber serialNumber) {
        if (!ready || serialNumber.isAfter(getSerialNumber())) {
            return Optional.empty();
        } else if (serialNumber.equals(getSerialNumber())) {
            return Optional.of(Delta.of(sessionId, serialNumber, Collections.emptySortedSet(), Collections.emptySortedSet()));
        } else {
            Optional<VersionedSet.Delta<RtrDataUnit>> delta = data.getDelta(serialNumber);
            return delta.map(d -> Delta.of(sessionId, getSerialNumber(), d.getAdditions(), d.getRemovals()));
        }
    }

    public synchronized Either<Delta, Content> getDeltaOrContent(SerialNumber serialNumber) {
        Optional<Delta> maybeDelta = getDeltaFrom(serialNumber);
        if (maybeDelta.isPresent()) {
            return Either.left(maybeDelta.get());
        } else {
            return Either.right(getCurrentContent());
        }
    }

    public synchronized Set<SerialNumber> forgetDeltasBefore(SerialNumber serialNumber) {
        return data.forgetDeltasBefore(serialNumber);
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
