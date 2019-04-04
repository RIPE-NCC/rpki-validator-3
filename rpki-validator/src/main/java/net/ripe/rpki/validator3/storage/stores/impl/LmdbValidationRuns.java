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
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.validator3.api.Paging;
import net.ripe.rpki.validator3.api.SearchTerm;
import net.ripe.rpki.validator3.api.Sorting;
import net.ripe.rpki.validator3.storage.FSTCoder;
import net.ripe.rpki.validator3.storage.Lmdb;
import net.ripe.rpki.validator3.storage.data.Key;
import net.ripe.rpki.validator3.storage.data.RpkiObject;
import net.ripe.rpki.validator3.storage.data.RpkiRepository;
import net.ripe.rpki.validator3.storage.data.TrustAnchor;
import net.ripe.rpki.validator3.storage.data.validation.CertificateTreeValidationRun;
import net.ripe.rpki.validator3.storage.data.validation.RpkiRepositoryValidationRun;
import net.ripe.rpki.validator3.storage.data.validation.RsyncRepositoryValidationRun;
import net.ripe.rpki.validator3.storage.data.validation.TrustAnchorValidationRun;
import net.ripe.rpki.validator3.storage.data.validation.ValidationCheck;
import net.ripe.rpki.validator3.storage.data.validation.ValidationRun;
import net.ripe.rpki.validator3.storage.lmdb.IxBase;
import net.ripe.rpki.validator3.storage.lmdb.IxMap;
import net.ripe.rpki.validator3.storage.lmdb.MultIxMap;
import net.ripe.rpki.validator3.storage.lmdb.Tx;
import net.ripe.rpki.validator3.storage.stores.RpkiObjectStore;
import net.ripe.rpki.validator3.storage.stores.RpkiRepositoryStore;
import net.ripe.rpki.validator3.storage.stores.TrustAnchorStore;
import net.ripe.rpki.validator3.storage.stores.ValidationRunStore;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
@Slf4j
public class LmdbValidationRuns implements ValidationRunStore {

    private static final String RPKI_VALIDATION_RUNS = "validation-runs";
    private static final String CT_RPKI_VALIDATION_RUNS = "ct-validation-runs";
    private static final String RS_RPKI_VALIDATION_RUNS = "rs-validation-runs";
    private static final String TA_RPKI_VALIDATION_RUNS = "ta-validation-runs";
    private static final String RPKI_VALIDATION_RUNS_TO_RPKI_OBJECTS = "validation-runs-to-trust-anchors";
    private static final String RPKI_RPKI_OBJECTS_TO_VALIDATION_RUNS = "trust-anchors-to-validation-runs";
    private static final String RPKI_VALIDATION_RUNS_TO_RPKI_REPOSITORIES = "validation-runs-to-repositories";
    private static final String RPKI_RPKI_REPOSITORIES_TO_VALIDATION_RUNS = "repositories-to-validation-runs";
    private static final String BY_TA_INDEX = "by-ta";
    private static final String BY_COMPLETED_AT_INDEX = "by-time";

    private final Sequences sequences;

    private final MultIxMap<Key> vr2ro;
    private final MultIxMap<Key> ro2vr;

    private final IxMap<Key> vr2repo;
    private final MultIxMap<Key> repo2vr;

    private final IxMap<CertificateTreeValidationRun> ctIxMap;
    private final IxMap<RpkiRepositoryValidationRun> repoIxMap;
    private final IxMap<TrustAnchorValidationRun> taIxMap;

    private final Map<String, IxMap<? extends ValidationRun>> maps = new HashMap<>();
    private final RpkiObjectStore rpkiObjectStore;

    @Autowired
    public LmdbValidationRuns(Lmdb lmdb,
                              TrustAnchorStore trustAnchorStore,
                              RpkiObjectStore rpkiObjectStore,
                              RpkiRepositoryStore rpkiRepositoryStore) {

        this.rpkiObjectStore = rpkiObjectStore;
        
        sequences = new Sequences(lmdb);

        ctIxMap = new IxMap<>(lmdb.getEnv(), CT_RPKI_VALIDATION_RUNS, new FSTCoder<>(),
                ImmutableMap.of(BY_TA_INDEX, vr -> Key.keys(vr.getTrustAnchor().key()),
                        BY_COMPLETED_AT_INDEX, this::completedAtIndexKeys));

        taIxMap = new IxMap<>(lmdb.getEnv(), TA_RPKI_VALIDATION_RUNS, new FSTCoder<>(),
                ImmutableMap.of(BY_TA_INDEX, vr -> Key.keys(vr.getTrustAnchor().key()),
                        BY_COMPLETED_AT_INDEX, this::completedAtIndexKeys));

        repoIxMap = new IxMap<>(lmdb.getEnv(), RS_RPKI_VALIDATION_RUNS, new FSTCoder<>(),
                ImmutableMap.of(BY_COMPLETED_AT_INDEX, this::completedAtIndexKeys));

        maps.put(CertificateTreeValidationRun.TYPE, ctIxMap);
        maps.put(TrustAnchorValidationRun.TYPE, taIxMap);
        maps.put(RpkiRepositoryValidationRun.TYPE, repoIxMap);

        vr2ro = new MultIxMap<>(lmdb.getEnv(), RPKI_VALIDATION_RUNS_TO_RPKI_OBJECTS, new FSTCoder<>());
        ro2vr = new MultIxMap<>(lmdb.getEnv(), RPKI_RPKI_OBJECTS_TO_VALIDATION_RUNS, new FSTCoder<>());

        vr2repo = new IxMap<>(lmdb.getEnv(), RPKI_VALIDATION_RUNS_TO_RPKI_REPOSITORIES, new FSTCoder<>());
        repo2vr = new MultIxMap<>(lmdb.getEnv(), RPKI_RPKI_REPOSITORIES_TO_VALIDATION_RUNS, new FSTCoder<>());

        rpkiObjectStore.onDelete((tx, rpkiObjectKey) -> {
            ro2vr.get(tx, rpkiObjectKey).forEach(validationRunId -> vr2ro.delete(tx, validationRunId));
            ro2vr.delete(tx, rpkiObjectKey);
        });
        rpkiRepositoryStore.onDelete((tx, repoKey) -> {
            repo2vr.get(tx, repoKey).forEach(vrId -> vr2repo.delete(tx, vrId));
            repo2vr.delete(tx, repoKey);
        });
        trustAnchorStore.onDelete(this::removeAllForTrustAnchor);
    }

    private Set<Key> completedAtIndexKeys(ValidationRun vr) {
        Instant completedAt = vr.getCompletedAt();
        return completedAt != null ? Key.keys(Key.of(completedAt.toEpochMilli())) : Collections.emptySet();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends ValidationRun> T add(Tx.Write tx, T vr) {
        vr.setId(Key.of(sequences.next(tx, RPKI_VALIDATION_RUNS + ":pk")));
        IxMap<ValidationRun> validationRunIxMap = pickIxMap(vr.getType());
        validationRunIxMap.put(tx, vr.key(), vr);
        return vr;
    }

    @Override
    public <T extends ValidationRun> void update(Tx.Write tx, T vr) {
        pickIxMap(vr.getType()).put(tx, vr.key(), vr);
    }

    @Override
    public <T extends ValidationRun> T get(Tx.Read tx, Class<T> type, long id) {
        return pickIxMap(type).get(tx, Key.of(id)).orElse(null);
    }


    @Override
    public <T extends ValidationRun> List<T> findAll(Tx.Read tx, Class<T> type) {
        IxMap<T> tIxMap = pickIxMap(type);
        return tIxMap.values(tx);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends ValidationRun> List<T> findLatestSuccessful(Tx.Read tx, Class<T> type) {
//        // TODO Compare it with the original, it's pretty hard to say if it's correct
        final IxMap<T> ixMap = pickIxMap(type);
        Map<Key, T> latestSuccessful = ixMap.getByIndexMax(BY_COMPLETED_AT_INDEX, tx, ValidationRun::isSucceeded);
        return new ArrayList<>(latestSuccessful.values());
    }

    @Override
    public Optional<TrustAnchorValidationRun> findLatestCompletedForTrustAnchor(Tx.Read tx, TrustAnchor trustAnchor) {
        return taIxMap.getByIndex(BY_TA_INDEX, tx, trustAnchor.key()).values().stream()
                .filter(vr -> vr.getCompletedAt() != null)
                .max(Comparator.comparing(ValidationRun::getCompletedAt));
    }

    public void removeAllForTrustAnchor(Tx.Write tx, Key trustAnchorKey) {
        Stream.of(taIxMap, ctIxMap).forEach(ixMap ->
                ixMap.getPkByIndex(BY_TA_INDEX, tx, trustAnchorKey)
                        .forEach(pk -> ixMap.delete(tx, pk)));
    }

    @Override
    public long removeOldValidationRuns(Tx.Write tx, Instant completedBefore) {
        AtomicLong count = new AtomicLong();
        Stream.of(ctIxMap, taIxMap, repoIxMap).forEach(ixMap -> {
            // Don't delete the most recent one
            final Set<Key> latestSuccessfulKeys = ixMap.getByIndexMax(BY_COMPLETED_AT_INDEX, tx, ValidationRun::isSucceeded).keySet();
            count.addAndGet(
                    ixMap.getByIndexLessPk(BY_COMPLETED_AT_INDEX, tx, Key.of(completedBefore.toEpochMilli()))
                            .stream()
                            .filter(pk -> !latestSuccessfulKeys.contains(pk))
                            .map(pk -> {
                                ixMap.delete(tx, pk);
                                return null;
                            })
                            .count()
            );
        });
        return count.get();
    }

    @Override
    public Stream<ValidationCheck> findValidationChecksForValidationRun(Tx.Read tx, long trustAnchorId, Paging paging, SearchTerm searchTerm, Sorting sorting) {
        return applyPaging(paging,
                applySorting(sorting,
                        applySearchTerm(searchTerm, Stream.concat(
                                streamValidationChecks(taIxMap, tx, trustAnchorId),
                                streamValidationChecks(ctIxMap, tx, trustAnchorId)))));
    }

    @Override
    public int countValidationChecksForValidationRun(Tx.Read tx, long trustAnchorId, SearchTerm searchTerm) {
        return (int) applySearchTerm(searchTerm, Stream.concat(
                streamValidationChecks(taIxMap, tx, trustAnchorId),
                streamValidationChecks(ctIxMap, tx, trustAnchorId))).count();
    }

    private <T extends ValidationRun> Stream<ValidationCheck> streamValidationChecks(IxMap<T> ixMap, Tx.Read tx, long trustAnchorId) {
        return ixMap.getByIndex(BY_TA_INDEX, tx, Key.of(trustAnchorId))
                .values()
                .stream()
                .filter(ValidationRun::isSucceeded)
                .max(Comparator.comparing(ValidationRun::getCompletedAt))
                .map(ValidationRun::getValidationChecks)
                .orElse(Collections.emptyList())
                .stream();
    }

    private Stream<ValidationCheck> applyPaging(Paging paging, Stream<ValidationCheck> validationChecks) {
        if (paging != null) {
            validationChecks = paging.apply(validationChecks);
        }
        return validationChecks;
    }

    private Stream<ValidationCheck> applySearchTerm(SearchTerm searchTerm, Stream<ValidationCheck> validationChecks) {
        if (searchTerm != null) {
            validationChecks = validationChecks.filter(vc -> {
                String term = searchTerm.asString().toLowerCase();
                return vc.getKey().toLowerCase().contains(term) ||
                        vc.getStatus().toString().toLowerCase().contains(term) ||
                        vc.getParameters().stream().anyMatch(p -> p.toLowerCase().contains(term));
            });
        }
        return validationChecks;
    }


    private Stream<ValidationCheck> applySorting(Sorting sorting, Stream<ValidationCheck> validationChecks) {
        if (sorting == null) {
            sorting = Sorting.of(Sorting.By.LOCATION, Sorting.Direction.ASC);
        }
        Comparator<ValidationCheck> comparator;
        switch (sorting.getBy()) {
            case KEY:
                comparator = Comparator.comparing(ValidationCheck::getKey);
                break;
            case STATUS:
                comparator = Comparator.comparing(ValidationCheck::getStatus);
                break;
            case LOCATION:
                comparator = Comparator.comparing(ValidationCheck::getLocation);
                break;
            default:
                comparator = Comparator.comparing(ValidationCheck::getCreatedAt);
                break;
        }
        return validationChecks.sorted(
                sorting.getDirection() == Sorting.Direction.DESC ?
                        comparator :
                        comparator.reversed());
    }

    @Override
    public void associate(Tx.Write writeTx, CertificateTreeValidationRun validationRun, RpkiObject rpkiObject) {
        associateVrAndRo(writeTx, validationRun, rpkiObject);
    }

    @Override
    public void associate(Tx.Write writeTx, RpkiRepositoryValidationRun validationRun, RpkiObject ro) {
        associateVrAndRo(writeTx, validationRun, ro);
    }

    @Override
    public void associate(Tx.Write writeTx, RsyncRepositoryValidationRun validationRun, RpkiRepository rpkiRepository) {
        vr2repo.put(writeTx, validationRun.key(), rpkiRepository.getId());
        repo2vr.put(writeTx, rpkiRepository.key(), validationRun.getId());
    }

    @Override
    public Set<Key> findAssociatedObjects(Tx.Read tx, CertificateTreeValidationRun validationRun) {
        return new HashSet<>(vr2ro.get(tx, validationRun.key()));
    }

    private void associateVrAndRo(Tx.Write writeTx, ValidationRun validationRun, RpkiObject rpkiObject) {
        vr2ro.put(writeTx, validationRun.key(), rpkiObject.key());
        ro2vr.put(writeTx, rpkiObject.key(), validationRun.key());
    }

    @Override
    public Stream<Pair<CertificateTreeValidationRun, RpkiObject>> findCurrentlyValidated(Tx.Read tx, RpkiObject.Type type) {
        return findLatestSuccessful(tx, CertificateTreeValidationRun.class)
                .stream()
                .flatMap(ct -> vr2ro.get(tx, ct.key())
                        .stream()
                        .map(roId -> rpkiObjectStore.get(tx, roId))
                        .filter(Optional::isPresent)
                        .map(Optional::get)
                        .filter(ro -> ro.getType() == type)
                        .map(ro -> Pair.of(ct, ro)));
    }

    @Override
    public void clear(Tx.Write tx) {
        Stream.of(ro2vr, vr2ro, repo2vr, vr2repo, ctIxMap, taIxMap, repoIxMap)
                .forEach(ixMap -> ixMap.clear(tx));
    }

    @SuppressWarnings("unchecked")
    private <T extends ValidationRun> IxMap<T> pickIxMap(String vrType) {
        final IxMap<? extends ValidationRun> ixMap = maps.get(vrType);
        if (ixMap == null) {
            throw new RuntimeException("Oops, you are looking for the " + vrType + " which is not here.");
        }
        return (IxMap<T>) ixMap;
    }

    private <T extends ValidationRun> IxMap<T> pickIxMap(Class<T> c) {
        if (CertificateTreeValidationRun.class.equals(c)) {
            return pickIxMap(CertificateTreeValidationRun.TYPE);
        }
        if (TrustAnchorValidationRun.class.equals(c)) {
            return pickIxMap(TrustAnchorValidationRun.TYPE);
        }
        if (RpkiRepositoryValidationRun.class.isAssignableFrom(c)) {
            return pickIxMap(RpkiRepositoryValidationRun.TYPE);
        }
        throw new RuntimeException("Oops, you are looking for the " + c + " which is not here.");
    }

}
