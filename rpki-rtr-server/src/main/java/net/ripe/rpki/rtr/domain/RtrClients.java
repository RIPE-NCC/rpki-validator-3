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

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.rtr.util.Locks;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Service
@Slf4j
public class RtrClients {
    public static final String RTRSERVER_CLIENTS_METRIC = "rtrserver.client.total";
    public static final String RTRSERVER_CLIENTS_METRIC_DESCRIPTION = "Number of RTR server clients";
    public static final String RTRSERVER_METRIC_TAG_OPERATION = "operation";

    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private final Set<RtrClient> clients = new HashSet<>();

    private final Counter clientRegisterCount;
    private final Counter clientUnregisterCount;
    private final Counter clientDisconnectInactiveCount;

    @Autowired
    public RtrClients(final MeterRegistry meterRegistry) {
        clientRegisterCount = Counter.builder(RTRSERVER_CLIENTS_METRIC)
                .tag(RTRSERVER_METRIC_TAG_OPERATION, "register")
                .description(RTRSERVER_CLIENTS_METRIC_DESCRIPTION)
                .register(meterRegistry);
        clientUnregisterCount = Counter.builder(RTRSERVER_CLIENTS_METRIC)
                .tag(RTRSERVER_METRIC_TAG_OPERATION, "unregister")
                .description(RTRSERVER_CLIENTS_METRIC_DESCRIPTION)
                .register(meterRegistry);
        clientDisconnectInactiveCount = Counter.builder(RTRSERVER_CLIENTS_METRIC)
                .tag(RTRSERVER_METRIC_TAG_OPERATION, "disconnect_inactive")
                .description(RTRSERVER_CLIENTS_METRIC_DESCRIPTION)
                .register(meterRegistry);
    }

    public void register(final RtrClient client) {
        boolean added = Locks.locked(lock.writeLock(), () -> clients.add(client));
        if (added) {
            log.info("registered client {}", client);
            clientRegisterCount.increment();
        }
    }

    public void clear() {
        Locks.locked(lock.writeLock(), clients::clear);
    }

    public void unregister(RtrClient client) {
        boolean removed = Locks.locked(lock.writeLock(), () -> clients.remove(client));
        if (removed) {
            log.info("unregistered client {}", client);
            clientUnregisterCount.increment();
        }
    }

    public Set<RtrClient> list() {
        return Locks.locked(lock.readLock(), () -> new HashSet<>(clients));
    }

    public void cacheUpdated(short sessionId, SerialNumber updatedSerialNumber) {
        Locks.locked(lock.readLock(), () ->
                clients.forEach(client -> client.cacheUpdated(sessionId, updatedSerialNumber))
        );
    }

    public Optional<SerialNumber> getLowestSerialNumber() {
        return Locks.locked(lock.readLock(), () ->
                clients.stream().map(RtrClient::getClientSerialNumber).min(Comparator.naturalOrder()));
    }

    public int disconnectInactive(Instant now) {
        return Locks.locked(lock.writeLock(), () -> {
            int before = clients.size();
            clients.removeIf(client -> client.disconnectIfInactive(now));
            final int change = before - clients.size();

            clientDisconnectInactiveCount.increment(change);
            return change;
        });
    }
}
