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

import net.ripe.rpki.validator3.IntegrationTest;
import net.ripe.rpki.validator3.TestObjects;
import net.ripe.rpki.validator3.api.util.InstantWithoutNanos;
import net.ripe.rpki.validator3.domain.ta.TrustAnchorsFactory;
import net.ripe.rpki.validator3.storage.data.Ref;
import net.ripe.rpki.validator3.storage.data.RpkiRepository;
import net.ripe.rpki.validator3.storage.data.TrustAnchor;
import net.ripe.rpki.validator3.storage.stores.RpkiRepositories;
import net.ripe.rpki.validator3.storage.stores.impl.GenericStorageTest;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit4.SpringRunner;

import java.time.ZonedDateTime;
import java.util.stream.Collectors;

import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

@RunWith(SpringRunner.class)
@IntegrationTest
public class RpkiRepositoryCleanupServiceTest extends GenericStorageTest {

    @Autowired
    private TrustAnchorsFactory factory;

    @Autowired
    private RpkiRepositoryCleanupService subject;

    @Autowired
    private RpkiRepositories rpkiRepositories;

    @Test
    public void should_delete_repositories_not_referenced_during_validation() throws Exception {
        TrustAnchor trustAnchor = TestObjects.newTrustAnchor();
        wtx0(tx -> this.getTrustAnchors().add(tx, trustAnchor));

        final Ref<TrustAnchor> trustAnchorRef = rtx(tx -> this.getTrustAnchors().makeRef(tx, trustAnchor.key()));
        RpkiRepository repository = wtx(tx -> this.getRpkiRepositories().register(tx,
                trustAnchorRef, "rsync://some.rsync.repo", RpkiRepository.Type.RSYNC));

        assertThat(rtx(tx -> rpkiRepositories.findRsyncRepositories(tx).collect(Collectors.toList())), hasSize(1));
        assertThat(subject.cleanupRpkiRepositories(), is(0L));

        repository.getTrustAnchors().put(trustAnchorRef, InstantWithoutNanos.from(ZonedDateTime.now().minusDays(20).toInstant()));
        wtx0(tx -> rpkiRepositories.update(tx, repository));

        assertThat(subject.cleanupRpkiRepositories(), is(1L));

        assertThat(rtx(tx -> rpkiRepositories.findRsyncRepositories(tx).collect(Collectors.toList())), is(empty()));
    }
}
