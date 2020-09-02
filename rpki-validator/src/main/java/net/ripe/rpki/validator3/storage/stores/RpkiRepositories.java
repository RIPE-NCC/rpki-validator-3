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
package net.ripe.rpki.validator3.storage.stores;

import net.ripe.rpki.validator3.api.Paging;
import net.ripe.rpki.validator3.api.SearchTerm;
import net.ripe.rpki.validator3.api.Sorting;
import net.ripe.rpki.validator3.api.util.InstantWithoutNanos;
import net.ripe.rpki.validator3.storage.Tx;
import net.ripe.rpki.validator3.storage.data.Key;
import net.ripe.rpki.validator3.storage.data.Ref;
import net.ripe.rpki.validator3.storage.data.RpkiRepository;
import net.ripe.rpki.validator3.storage.data.TrustAnchor;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

public interface RpkiRepositories extends GenericStore<RpkiRepository> {

    RpkiRepository register(Tx.Write tx, Ref<TrustAnchor> trustAnchor, String uri, RpkiRepository.Type type);

    void update(Tx.Write tx, RpkiRepository rpkiRepository);

    Optional<RpkiRepository> findByURI(Tx.Read tx, String uri);

    Optional<RpkiRepository> get(Tx.Read tx, Key id);

    Stream<RpkiRepository> findAll(Tx.Read tx, RpkiRepository.Status optionalStatus, Key taId, boolean hideChildrenOfDownloadedParent,
                                   SearchTerm searchTerm, Sorting sorting, Paging paging);

    long countAll(Tx.Read tx, RpkiRepository.Status optionalStatus, Key taId, boolean hideChildrenOfDownloadedParent, SearchTerm searchTerm);

    Map<RpkiRepository.Status, Long> countByStatus(Tx.Read tx, Key taId, boolean hideChildrenOfDownloadedParent);

    default Stream<RpkiRepository> findAll(Tx.Read tx, RpkiRepository.Status optionalStatus, Key taId) {
        return findAll(tx, optionalStatus, taId, false, null, null, null);
    }

    default Stream<RpkiRepository> findAll(Tx.Read tx, Key taId) {
        return findAll(tx, null, taId, false, null, null, null);
    }

    Stream<RpkiRepository> findRsyncRepositories(Tx.Read tx);

    Stream<RpkiRepository> findRrdpRepositories(Tx.Read tx);

    void removeAllForTrustAnchor(Tx.Write tx, TrustAnchor trustAnchor);

    void remove(Tx.Write tx, Key key);

    Collection<RpkiRepository> findByTrustAnchor(Tx.Read tx, Key key);

    long deleteUnreferencedRepositories(Tx.Write tx, InstantWithoutNanos unreferencedSince);
}
