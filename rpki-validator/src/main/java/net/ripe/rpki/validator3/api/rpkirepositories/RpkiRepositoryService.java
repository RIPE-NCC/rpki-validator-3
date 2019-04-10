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
package net.ripe.rpki.validator3.api.rpkirepositories;

import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.validator3.background.ValidationScheduler;
import net.ripe.rpki.validator3.storage.Lmdb;
import net.ripe.rpki.validator3.storage.stores.RpkiRepositoryStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import javax.annotation.PostConstruct;

@Component
@Validated
@Slf4j
public class RpkiRepositoryService {

    private final RpkiRepositoryStore rpkiRepositoryStore;

    private final ValidationScheduler validationScheduler;

    private final Lmdb lmdb;

    @Autowired
    public RpkiRepositoryService(RpkiRepositoryStore rpkiRepositoryStore, ValidationScheduler validationScheduler, Lmdb lmdb) {
        this.rpkiRepositoryStore = rpkiRepositoryStore;
        this.validationScheduler = validationScheduler;
        this.lmdb = lmdb;
    }

    @PostConstruct
    public void scheduleRpkiRepositoryValidation() {
        log.info("Schedule RPKI validation for the existing repositories");
        lmdb.writeTx0(tx ->
                rpkiRepositoryStore.findRrdpRepositories(tx).forEach(r -> {
                    tx.onCommit(() -> validationScheduler.addRpkiRepository(r));
                    log.info("Scheduled {} for validation.", r);
                }));
    }
}
