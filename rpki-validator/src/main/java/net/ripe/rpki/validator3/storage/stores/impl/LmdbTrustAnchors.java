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
import net.ripe.rpki.validator3.api.trustanchors.TaStatus;
import net.ripe.rpki.validator3.storage.FSTCoder;
import net.ripe.rpki.validator3.storage.Lmdb;
import net.ripe.rpki.validator3.storage.data.Key;
import net.ripe.rpki.validator3.storage.data.TrustAnchor;
import net.ripe.rpki.validator3.storage.lmdb.IxMap;
import net.ripe.rpki.validator3.storage.lmdb.Tx;
import net.ripe.rpki.validator3.storage.stores.GenericStoreImpl;
import net.ripe.rpki.validator3.storage.stores.TrustAnchorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Component
public class LmdbTrustAnchors extends GenericStoreImpl<TrustAnchor> implements TrustAnchorStore {

    private static final String BY_NAME = "by-name";
    private static final String BY_SUBJECT_KEY_INFO = "by-ski";

    private final IxMap<TrustAnchor> ixMap;
    private final Sequences sequences;

    @Autowired
    public LmdbTrustAnchors(Lmdb lmdb, Sequences sequences) {
        this.ixMap = new IxMap<>(
                lmdb.getEnv(),
                TrustAnchorStore.TRUST_ANCHORS,
                new FSTCoder<>(),
                ImmutableMap.of(
                        BY_NAME, ta -> Key.keys(Key.of(ta.getName())),
                        BY_SUBJECT_KEY_INFO, ta -> Key.keys(Key.of(ta.getSubjectPublicKeyInfo())))
        );
        this.sequences = sequences;
    }

    @Override
    public TrustAnchor add(Tx.Write tx, TrustAnchor trustAnchor) {
        trustAnchor.setId(Key.of(sequences.next(tx, TrustAnchorStore.TRUST_ANCHORS + ":pk")));
        ixMap.put(tx, trustAnchor.key(), trustAnchor);
        return trustAnchor;
    }

    @Override
    public void update(Tx.Write tx, TrustAnchor trustAnchor) {
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
        return ixMap.getByIndex(BY_NAME, tx, Key.of(name)).values();
    }

    @Override
    public Optional<TrustAnchor> findBySubjectPublicKeyInfo(Tx.Read tx, String subjectPublicKeyInfo) {
        return ixMap.getByIndex(BY_SUBJECT_KEY_INFO, tx, Key.of(subjectPublicKeyInfo)).values().stream().findFirst();
    }

    @Override
    public boolean allInitialCertificateTreeValidationRunsCompleted(Tx.Read tx) {
        return ixMap.values(tx).stream().allMatch(TrustAnchor::isInitialCertificateTreeValidationRunCompleted);
    }

    @Override
    public List<TaStatus> getStatuses(Tx.Read tx) {
        // TODO Implement
        return null;
    }

    @Override
    protected IxMap<TrustAnchor> ixMap() {
        return ixMap;
    }
}
