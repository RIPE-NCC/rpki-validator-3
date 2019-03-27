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
package net.ripe.rpki.validator3.storage.stores.impl;

import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.validator3.IntegrationTest;
import net.ripe.rpki.validator3.TestObjects;
import net.ripe.rpki.validator3.api.SearchTerm;
import net.ripe.rpki.validator3.storage.data.RpkiRepository;
import net.ripe.rpki.validator3.storage.data.TrustAnchor;
import net.ripe.rpki.validator3.storage.lmdb.Tx;
import net.ripe.rpki.validator3.storage.stores.Id;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Map;

import static org.junit.Assert.assertEquals;

@RunWith(SpringRunner.class)
@IntegrationTest
@Slf4j
public class LmdbRpkiRepostioriesTest extends GenericStorageTest {

    private TrustAnchor trustAnchor;
    private RpkiRepository rsyncRepo;

    @Before
    public void setup() {
        trustAnchor = TestObjects.newTrustAnchor();
        wtx0(tx -> getTrustAnchorStore().add(tx, trustAnchor));

        rsyncRepo = Tx.with(getLmdb().writeTx(), tx -> getRpkiRepositoryStore().register(tx,
                trustAnchor, "rsync://some.rsync.repo", RpkiRepository.Type.RSYNC));
    }

    @Test
    public void after_setup_there_is_one_rpki_repository() {
        long countAll = rtx(tx -> getRpkiRepositoryStore().countAll(tx, RpkiRepository.Status.PENDING,
                trustAnchor.key(), true, new SearchTerm("some")));
        assertEquals(1, countAll);
    }

    @Test
    public void after_setup_there_is_one_pending() {
        Map<RpkiRepository.Status, Long> statuses = rtx(tx -> getRpkiRepositoryStore().countByStatus(tx, trustAnchor.key(), true));
        long countPending = statuses.get(RpkiRepository.Status.PENDING);
        assertEquals(1, countPending);
    }

    @Test
    public void after_remove_count_should_be_zero() {
        long countAll = wtx(tx -> {
            getRpkiRepositoryStore().removeAllForTrustAnchor(tx, trustAnchor);
            return getRpkiRepositoryStore().countAll(tx, RpkiRepository.Status.PENDING, trustAnchor.key(), true, new SearchTerm("some"));
        });
        assertEquals(0, countAll);
    }

    @Test
    public void after_remove_repository_count_should_be_zero() {
        long countAll = wtx(tx -> {
            getRpkiRepositoryStore().remove(tx, rsyncRepo.key());
            return getRpkiRepositoryStore().countAll(tx, RpkiRepository.Status.PENDING, trustAnchor.key(), true, new SearchTerm("some"));
        });
        assertEquals(0, countAll);
    }

}