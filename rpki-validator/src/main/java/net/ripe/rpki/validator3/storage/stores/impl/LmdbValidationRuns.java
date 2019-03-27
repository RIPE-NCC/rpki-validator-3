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

import net.ripe.rpki.validator3.api.Paging;
import net.ripe.rpki.validator3.api.SearchTerm;
import net.ripe.rpki.validator3.api.Sorting;
import net.ripe.rpki.validator3.storage.FSTCoder;
import net.ripe.rpki.validator3.storage.Lmdb;
import net.ripe.rpki.validator3.storage.data.RpkiObject;
import net.ripe.rpki.validator3.storage.data.RpkiRepository;
import net.ripe.rpki.validator3.storage.data.SId;
import net.ripe.rpki.validator3.storage.data.validation.CertificateTreeValidationRun;
import net.ripe.rpki.validator3.storage.data.validation.RpkiRepositoryValidationRun;
import net.ripe.rpki.validator3.storage.data.validation.RrdpRepositoryValidationRun;
import net.ripe.rpki.validator3.storage.data.validation.RsyncRepositoryValidationRun;
import net.ripe.rpki.validator3.storage.data.TrustAnchor;
import net.ripe.rpki.validator3.storage.data.validation.TrustAnchorValidationRun;
import net.ripe.rpki.validator3.storage.data.validation.ValidationCheck;
import net.ripe.rpki.validator3.storage.data.validation.ValidationRun;
import net.ripe.rpki.validator3.storage.lmdb.IxMap;
import net.ripe.rpki.validator3.storage.data.Key;
import net.ripe.rpki.validator3.storage.lmdb.MultIxMap;
import net.ripe.rpki.validator3.storage.data.Ref;
import net.ripe.rpki.validator3.storage.lmdb.Tx;
import net.ripe.rpki.validator3.storage.stores.GenericStoreImpl;
import net.ripe.rpki.validator3.storage.stores.TrustAnchorStore;
import net.ripe.rpki.validator3.storage.stores.ValidationRunStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * TODO Implement missing methods here
 */
@Component
public class LmdbValidationRuns extends GenericStoreImpl<ValidationRun> implements ValidationRunStore {

    private static final String RPKI_VALIDATION_RUNS = "validation-runs";
    private static final String RPKI_VALIDATION_RUNS_TO_TRUST_ANCHORS = "validation-runs-to-trust-anchors";
    private static final String RPKI_TRUST_ANCHORS_TO_VALIDATION_RUNS_= "trust-anchors-to-validation-runs";

    private final IxMap<ValidationRun> ixMap;
    private final Sequences sequences;

    private final IxMap<Key> vr2ta;
    private final MultIxMap<SId<ValidationRun>> ta2vr;

    private final TrustAnchorStore trustAnchors;

    @Autowired
    public LmdbValidationRuns(Lmdb lmdb, TrustAnchorStore trustAnchors) {
        sequences = new Sequences(lmdb);
        ixMap = new IxMap<>(lmdb.getEnv(), RPKI_VALIDATION_RUNS, new FSTCoder<>());
        vr2ta = new IxMap<>(lmdb.getEnv(), RPKI_VALIDATION_RUNS_TO_TRUST_ANCHORS, new FSTCoder<>());
        ta2vr = new MultIxMap<>(lmdb.getEnv(), RPKI_TRUST_ANCHORS_TO_VALIDATION_RUNS_, new FSTCoder<>());
        this.trustAnchors = trustAnchors;
    }


    @Override
    public void add(Tx.Write tx, ValidationRun validationRun) {
        final SId<ValidationRun> vrId = SId.of(sequences.next(tx, RPKI_VALIDATION_RUNS + ":pk"));
        validationRun.setId(vrId);
        ixMap.put(tx, vrId.key(), validationRun);
        if (validationRun instanceof CertificateTreeValidationRun) {
            CertificateTreeValidationRun vr = (CertificateTreeValidationRun) validationRun;

            // don't do anything here as Set<RpkiObject> validatedObjects will be associated
            // with the validation run separately
            Set<RpkiObject> validatedObjects = vr.getValidatedObjects();

            // don't do anything here as Ref<TrustAnchor> will be serialised
            Ref<TrustAnchor> trustAnchor = vr.getTrustAnchor();

        } else if (validationRun instanceof TrustAnchorValidationRun) {
            TrustAnchorValidationRun vr = (TrustAnchorValidationRun) validationRun;
            final Key taId = vr.getTrustAnchor().getId().key();
            vr2ta.put(tx, vrId.key(), taId);
            ta2vr.put(tx, taId, vrId);
        } else if (validationRun instanceof RsyncRepositoryValidationRun) {
            RsyncRepositoryValidationRun vr = (RsyncRepositoryValidationRun) validationRun;
            Set<Ref<RpkiRepository>> rpkiRepositories = vr.getRpkiRepositories();
        } else if (validationRun instanceof RrdpRepositoryValidationRun) {
            RrdpRepositoryValidationRun vr = (RrdpRepositoryValidationRun) validationRun;
            Ref<RpkiRepository> rpkiRepository = vr.getRpkiRepository();
        }
    }

    @Override
    public void update(Tx.Write tx, ValidationRun validationRun) {
        ixMap.put(tx, validationRun.key(), validationRun);
    }

    @Override
    public void removeAllForTrustAnchor(Tx.Write tx, TrustAnchor trustAnchor) {
        // TODO Implement
    }

    @Override
    public <T extends ValidationRun> T get(Class<T> type, long id) {
        return null;
    }

    @Override
    public <T extends ValidationRun> List<T> findAll(Class<T> type) {
        return null;
    }

    @Override
    public <T extends ValidationRun> List<T> findLatestSuccessful(Class<T> type) {
        return null;
    }

    @Override
    public Optional<TrustAnchorValidationRun> findLatestCompletedForTrustAnchor(TrustAnchor trustAnchor) {
        return Optional.empty();
    }

    @Override
    public void runCertificateTreeValidation(TrustAnchor trustAnchor) {

    }

    @Override
    public void removeAllForRpkiRepository(RpkiRepository repository) {

    }

    @Override
    public long removeOldValidationRuns(Instant completedBefore) {
        return 0;
    }

    @Override
    public Stream<ValidationCheck> findValidationChecksForValidationRun(long validationRunId, Paging paging, SearchTerm searchTerm, Sorting sorting) {
        return null;
    }

    @Override
    public int countValidationChecksForValidationRun(long validationRunId, SearchTerm searchTerm) {
        return 0;
    }

    @Override
    public void associate(Tx.Write writeTx, CertificateTreeValidationRun validationRun, RpkiObject rpkiObject) {
        // TODO Implement
    }

    @Override
    public void associate(Tx.Write writeTx, RsyncRepositoryValidationRun validationRun, RpkiRepository r) {
        // TODO Implement
    }

    @Override
    public void associate(Tx.Write writeTx, RpkiRepositoryValidationRun validationRun, RpkiObject rpkiObject) {
        // TODO Implement
    }

    @Override
    protected IxMap<ValidationRun> ixMap() {
        return ixMap;
    }
}
