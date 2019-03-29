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
import net.ripe.rpki.validator3.storage.Key;
import net.ripe.rpki.validator3.storage.data.Ref;
import net.ripe.rpki.validator3.storage.data.RpkiObject;
import net.ripe.rpki.validator3.storage.data.RpkiRepository;
import net.ripe.rpki.validator3.storage.data.SId;
import net.ripe.rpki.validator3.storage.data.TrustAnchor;
import net.ripe.rpki.validator3.storage.data.validation.CertificateTreeValidationRun;
import net.ripe.rpki.validator3.storage.data.validation.RpkiRepositoryValidationRun;
import net.ripe.rpki.validator3.storage.data.validation.RrdpRepositoryValidationRun;
import net.ripe.rpki.validator3.storage.data.validation.RsyncRepositoryValidationRun;
import net.ripe.rpki.validator3.storage.data.validation.TrustAnchorValidationRun;
import net.ripe.rpki.validator3.storage.data.validation.ValidationCheck;
import net.ripe.rpki.validator3.storage.data.validation.ValidationRun;
import net.ripe.rpki.validator3.storage.lmdb.IxMap;
import net.ripe.rpki.validator3.storage.lmdb.MultIxMap;
import net.ripe.rpki.validator3.storage.lmdb.Tx;
import net.ripe.rpki.validator3.storage.stores.GenericStoreImpl;
import net.ripe.rpki.validator3.storage.stores.RpkiObjectStore;
import net.ripe.rpki.validator3.storage.stores.TrustAnchorStore;
import net.ripe.rpki.validator3.storage.stores.ValidationRunStore;
import net.ripe.rpki.validator3.util.Rsync;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * TODO Implement missing methods here
 */
@Component
public class LmdbValidationRuns extends GenericStoreImpl<ValidationRun> implements ValidationRunStore {

    private static final String RPKI_VALIDATION_RUNS = "validation-runs";
    private static final String CT_RPKI_VALIDATION_RUNS = "ct-validation-runs";
    private static final String RR_RPKI_VALIDATION_RUNS = "rr-validation-runs";
    private static final String RS_RPKI_VALIDATION_RUNS = "rs-validation-runs";
    private static final String TA_RPKI_VALIDATION_RUNS = "ta-validation-runs";
    private static final String RPKI_VALIDATION_RUNS_TO_TRUST_ANCHORS = "validation-runs-to-trust-anchors";
    private static final String RPKI_TRUST_ANCHORS_TO_VALIDATION_RUNS_ = "trust-anchors-to-validation-runs";
    private static final String BY_TIME_SUCCESSFUL_INDEX = "by-time-successful";
    private static final String BY_TA_INDEX = "by-ta";
    private static final String BY_REPOSITORY_INDEX = "by-repository";

    private final IxMap<ValidationRun> ixMap;
    private final Sequences sequences;

    private final IxMap<Key> vr2ta;

    private final MultIxMap<SId<ValidationRun>> ta2vr;

    private final TrustAnchorStore trustAnchorStore;

    private final RpkiObjectStore rpkiObjectStore;

    private final IxMap<CertificateTreeValidationRun> ctIxMap;
    private final IxMap<RrdpRepositoryValidationRun> rrIxMap;
    private final IxMap<RsyncRepositoryValidationRun> rsIxMap;
    private final IxMap<TrustAnchorValidationRun> taIxMap;

    @Autowired
    public LmdbValidationRuns(Lmdb lmdb, TrustAnchorStore trustAnchorStore, RpkiObjectStore rpkiObjectStore) {
        sequences = new Sequences(lmdb);
        ixMap = new IxMap<>(lmdb.getEnv(), RPKI_VALIDATION_RUNS, new FSTCoder<>());
        vr2ta = new IxMap<>(lmdb.getEnv(), RPKI_VALIDATION_RUNS_TO_TRUST_ANCHORS, new FSTCoder<>());
        ta2vr = new MultIxMap<>(lmdb.getEnv(), RPKI_TRUST_ANCHORS_TO_VALIDATION_RUNS_, new FSTCoder<>());
        this.trustAnchorStore = trustAnchorStore;
        this.rpkiObjectStore = rpkiObjectStore;

        ctIxMap = new IxMap<>(lmdb.getEnv(), CT_RPKI_VALIDATION_RUNS, new FSTCoder<>(),
                ImmutableMap.of(
                        BY_TIME_SUCCESSFUL_INDEX, LmdbValidationRuns::successfulByTimeIndex,
                        BY_TA_INDEX, vr -> Key.keys(vr.getTrustAnchor().key())));

        rrIxMap = new IxMap<>(lmdb.getEnv(), RR_RPKI_VALIDATION_RUNS, new FSTCoder<>(),
                ImmutableMap.of(
                        BY_TIME_SUCCESSFUL_INDEX, LmdbValidationRuns::successfulByTimeIndex,
                        BY_REPOSITORY_INDEX, vr -> Key.keys(vr.getRpkiRepository().key())));

        rsIxMap = new IxMap<>(lmdb.getEnv(), RS_RPKI_VALIDATION_RUNS, new FSTCoder<>(),
                ImmutableMap.of(BY_TIME_SUCCESSFUL_INDEX, LmdbValidationRuns::successfulByTimeIndex));

        taIxMap = new IxMap<>(lmdb.getEnv(), TA_RPKI_VALIDATION_RUNS, new FSTCoder<>(),
                ImmutableMap.of(
                        BY_TIME_SUCCESSFUL_INDEX, LmdbValidationRuns::successfulByTimeIndex,
                        BY_TA_INDEX, vr -> Key.keys(vr.getTrustAnchor().key())));

        this.rpkiObjectStore.onDelete((tx, roKey) -> {
            // TODO delete associations
        });
        this.trustAnchorStore.onDelete((tx, taKey) -> {
            // TODO delete associations
        });
    }

    // Index function for indexing "last successful" queries
    private static Set<Key> successfulByTimeIndex(ValidationRun vr) {
        if (vr.isFailed()) {
            return Collections.emptySet();
        }
        return Key.keys(Key.of(vr.getCompletedAt().toEpochMilli()));
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends ValidationRun> void add(Tx.Write tx, T validationRun) {
        final SId<ValidationRun> vrId = SId.of(sequences.next(tx, RPKI_VALIDATION_RUNS + ":pk"));
        validationRun.setId(vrId);
        IxMap<T> ixMap = (IxMap<T>) pickIxMap(validationRun.getClass());
        ixMap.put(tx, vrId.key(), validationRun);
    }

    @SuppressWarnings("unchecked")
    private <T extends ValidationRun> IxMap<T> pickIxMap(Class<T> c) {
        if (CertificateTreeValidationRun.class.equals(c)) {
            return (IxMap<T>) ctIxMap;
        }
        if (RrdpRepositoryValidationRun.class.equals(c)) {
            return (IxMap<T>) rrIxMap;
        }
        if (RsyncRepositoryValidationRun.class.equals(c)) {
            return (IxMap<T>) rsIxMap;
        }
        if (TrustAnchorValidationRun.class.equals(c)) {
            return (IxMap<T>) taIxMap;
        }
        throw new RuntimeException("Oops, you are looking for the " + c + " which is not here.");
    }

    @Override
    public <T extends ValidationRun> void update(Tx.Write tx, T validationRun) {
        IxMap<T> ixMap = (IxMap<T>) pickIxMap(validationRun.getClass());
        ixMap.put(tx, validationRun.key(), validationRun);
    }

    @Override
    public <T extends ValidationRun> T get(Tx.Read tx, Class<T> type, long id) {
        return pickIxMap(type).get(tx, Key.of(id)).orElse(null);
    }

    @Override
    public <T extends ValidationRun> List<T> findAll(Tx.Read tx, Class<T> type) {
        return pickIxMap(type).values(tx);
    }

    @Override
    public <T extends ValidationRun> List<T> findLatestSuccessful(Tx.Read tx, Class<T> type) {
        final IxMap<T> ixMap = pickIxMap(type);
        return ixMap.get(tx, ixMap.getMaxByIndex(BY_TIME_SUCCESSFUL_INDEX, tx));
    }

    @Override
    public Optional<TrustAnchorValidationRun> findLatestCompletedForTrustAnchor(Tx.Read tx, TrustAnchor trustAnchor) {
        return taIxMap.getByIndex(BY_TA_INDEX, tx, trustAnchor.key()).stream()
                .filter(vr -> vr.getCompletedAt() != null)
                .max(Comparator.comparing(ValidationRun::getCompletedAt));
    }

    @Override
    public void removeAllForTrustAnchor(Tx.Write tx, TrustAnchor trustAnchor) {
        Stream.of(taIxMap, ctIxMap).forEach(ixMap ->
                ixMap.getPkByIndex(BY_TA_INDEX, tx, trustAnchor.key())
                        .forEach(pk -> ixMap.delete(tx, pk)));
    }

    @Override
    public void removeAllForRpkiRepository(Tx.Write tx, RpkiRepository repository) {
        rrIxMap.getPkByIndex(BY_TA_INDEX, tx, repository.key())
                .forEach(pk -> ixMap.delete(tx, pk));
    }

    @Override
    public long removeOldValidationRuns(Tx.Write tx, Instant completedBefore) {
        // TODO Don't delete the last one!
        return 0;
    }

    @Override
    public Stream<ValidationCheck> findValidationChecksForValidationRun(Tx.Read tx, long validationRunId, Paging paging, SearchTerm searchTerm, Sorting sorting) {
        return null;
    }

    @Override
    public int countValidationChecksForValidationRun(Tx.Read tx, long validationRunId, SearchTerm searchTerm) {
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
