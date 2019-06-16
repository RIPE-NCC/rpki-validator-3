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
import net.ripe.rpki.validator3.storage.IxMap;
import net.ripe.rpki.validator3.storage.Storage;
import net.ripe.rpki.validator3.storage.Tx;
import net.ripe.rpki.validator3.storage.data.Key;
import net.ripe.rpki.validator3.storage.data.TrustAnchor;
import net.ripe.rpki.validator3.storage.stores.GenericStoreImpl;
import net.ripe.rpki.validator3.storage.stores.TrustAnchors;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Singleton
public class TrustAnchorStore extends GenericStoreImpl<TrustAnchor> implements TrustAnchors {

    private final IxMap<TrustAnchor> ixMap;
    private final SequencesStore sequences;

    @Inject
    public TrustAnchorStore(Storage storage,
                            SequencesStore sequences) {
        this.ixMap = storage.createIxMap(
                TrustAnchors.TRUST_ANCHORS,
                ImmutableMap.of(),
                TrustAnchor.class);
        this.sequences = sequences;

    }

    @Override
    public TrustAnchor add(Tx.Write tx, TrustAnchor trustAnchor) {
        trustAnchor.setId(Key.of(sequences.next(tx, TrustAnchors.TRUST_ANCHORS + ":pk")));
        ixMap.put(tx, trustAnchor.key(), trustAnchor);
        return trustAnchor;
    }

    @Override
    public void update(Tx.Write tx, TrustAnchor trustAnchor) {
        trustAnchor.setUpdatedAt(Instant.now());
        ixMap.put(tx, trustAnchor.key(), trustAnchor);
    }

    @Override
    public void remove(Tx.Write tx, TrustAnchor trustAnchor) {
        ixMap.delete(tx, trustAnchor.key());
    }

    @Override
    public Optional<TrustAnchor> get(Tx.Read tx, Key id) {
        return ixMap.get(tx, id);
    }

    @Override
    public List<TrustAnchor> findAll(Tx.Read tx) {
        return ixMap.values(tx);
    }

    @Override
    public Collection<TrustAnchor> findByName(Tx.Read tx, String name) {
        return findAll(tx).stream()
                .filter(ta -> ta.getName().equals(name))
                .collect(Collectors.toList());
    }

    @Override
    public Optional<TrustAnchor> findBySubjectPublicKeyInfo(Tx.Read tx, String subjectPublicKeyInfo) {
        return findAll(tx).stream()
                .filter(ta -> ta.getSubjectPublicKeyInfo().equals(subjectPublicKeyInfo))
                .findFirst();
    }

    @Override
    public boolean allInitialCertificateTreeValidationRunsCompleted(Tx.Read tx) {
        return findAll(tx).stream().allMatch(TrustAnchor::isInitialCertificateTreeValidationRunCompleted);
    }

    @Override
    protected IxMap<TrustAnchor> ixMap() {
        return ixMap;
    }
}
