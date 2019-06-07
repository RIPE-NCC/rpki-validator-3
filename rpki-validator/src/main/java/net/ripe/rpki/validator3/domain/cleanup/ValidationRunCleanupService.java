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
package net.ripe.rpki.validator3.domain.cleanup;

import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.validator3.storage.lmdb.Storage;
import net.ripe.rpki.validator3.storage.stores.ValidationRuns;
import net.ripe.rpki.validator3.util.Time;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@Slf4j
public class ValidationRunCleanupService {

    @Autowired
    private ValidationRuns validationRuns;

    private final Duration cleanupGraceDuration;

    private final Storage storage;

    public ValidationRunCleanupService(@Value("${rpki.validator.validation.run.cleanup.grace.duration}") String cleanupGraceDuration,
                                       Storage storage) {
        this.cleanupGraceDuration = Duration.parse(cleanupGraceDuration);
        this.storage = storage;
    }

    public void cleanupValidationRuns() {
        AtomicInteger oldCount = new AtomicInteger();
        AtomicInteger orphanCount = new AtomicInteger();
        Instant completedBefore = Instant.now().minus(cleanupGraceDuration);
        Long t = Time.timed(() -> {
            // Delete all validation runs older than `cleanupGraceDuration` that have a later validation run.
            oldCount.set(storage.writeTx(tx -> validationRuns.removeOldValidationRuns(tx, completedBefore)));
            orphanCount.set(storage.writeTx(tx -> validationRuns.removeOrphanValidationRunAssociations(tx)));
        });
        log.info("Removed {} old validation runs and {} orphans in {}ms", oldCount.get(), orphanCount.get(), t);
    }
}
