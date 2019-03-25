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

import com.google.common.collect.ImmutableMap;
import net.ripe.rpki.validator3.api.Paging;
import net.ripe.rpki.validator3.api.SearchTerm;
import net.ripe.rpki.validator3.api.Sorting;
import net.ripe.rpki.validator3.storage.FSTCoder;
import net.ripe.rpki.validator3.storage.Lmdb;
import net.ripe.rpki.validator3.storage.data.RpkiRepository;
import net.ripe.rpki.validator3.storage.data.TrustAnchor;
import net.ripe.rpki.validator3.storage.lmdb.IxMap;
import net.ripe.rpki.validator3.storage.lmdb.Key;
import net.ripe.rpki.validator3.storage.lmdb.Tx;
import net.ripe.rpki.validator3.storage.stores.RpkiRepostioryStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
public class LmdbRpkiRepostiories implements RpkiRepostioryStore {

    private static final String RPKI_REPOSITORIES = "rpki-repositories";
    private static final String BY_URI = "by-uri";
    private static final String BY_TA = "by-ta";

    private final IxMap<RpkiRepository> ixMap;

    @Autowired
    public LmdbRpkiRepostiories(Lmdb lmdb) {
        this.ixMap = new IxMap<>(
                lmdb.getEnv(),
                RPKI_REPOSITORIES,
                new FSTCoder<>(),
                ImmutableMap.of(
                        BY_URI, r -> Key.keys(Key.of(r.getLocationUri())),
                        BY_TA, r -> r.getTrustAnchors().stream().map(ta -> Key.of(ta.getId())).collect(Collectors.toSet())
                )
        );
    }

    @Override
    public RpkiRepository register(TrustAnchor trustAnchor, String uri, RpkiRepository.Type type) {
        return null;
    }

    @Override
    public Optional<RpkiRepository> findByURI(Tx.Read tx, String uri) {
        return ixMap.getByIndex(BY_URI, tx, Key.of(uri)).stream().findFirst();
    }

    @Override
    public Optional<RpkiRepository> get(Tx.Read tx, Key id) {
        return ixMap.get(tx, id);
    }

    @Override
    public void update(Tx.Write tx, RpkiRepository rpkiRepository) {
        ixMap.put(tx, rpkiRepository.getId(), rpkiRepository);
    }

    @Override
    public Stream<RpkiRepository> findAll(RpkiRepository.Status optionalStatus, Long taId,
                                          boolean hideChildrenOfDownloadedParent, SearchTerm searchTerm, Sorting sorting, Paging paging) {
        return null;
    }

    @Override
    public long countAll(RpkiRepository.Status optionalStatus, Long taId,
                         boolean hideChildrenOfDownloadedParent, SearchTerm searchTerm) {
        return 0;
    }

    @Override
    public Map<RpkiRepository.Status, Long> countByStatus(Long taId, boolean hideChildrenOfDownloadedParent) {
        return null;
    }

    @Override
    public Stream<RpkiRepository> findAll(RpkiRepository.Status optionalStatus, Long taId) {
        return null;
    }

    @Override
    public Stream<RpkiRepository> findAll(Long taId) {
        return null;
    }

    @Override
    public Stream<RpkiRepository> findRsyncRepositories(Tx.Read tx) {
        return ixMap.values(tx).stream()
                .filter(r -> r.getType() == RpkiRepository.Type.RSYNC || r.getType() == RpkiRepository.Type.RSYNC_PREFETCH);
    }

    @Override
    public Stream<RpkiRepository> findRrdpRepositories(Tx.Read tx) {
        return ixMap.values(tx).stream()
                .filter(r -> r.getType() == RpkiRepository.Type.RRDP);
    }

    @Override
    public void removeAllForTrustAnchor(TrustAnchor trustAnchor) {

    }

    @Override
    public void remove(long id) {

    }
}
