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
import net.ripe.rpki.validator3.storage.data.RpkiObject;
import net.ripe.rpki.validator3.storage.data.RpkiRepository;
import net.ripe.rpki.validator3.storage.data.SId;
import net.ripe.rpki.validator3.storage.data.TrustAnchor;
import net.ripe.rpki.validator3.storage.data.validation.CertificateTreeValidationRun;
import net.ripe.rpki.validator3.storage.data.validation.RpkiRepositoryValidationRun;
import net.ripe.rpki.validator3.storage.data.validation.RsyncRepositoryValidationRun;
import net.ripe.rpki.validator3.storage.data.validation.TrustAnchorValidationRun;
import net.ripe.rpki.validator3.storage.data.validation.ValidationCheck;
import net.ripe.rpki.validator3.storage.data.validation.ValidationRun;
import net.ripe.rpki.validator3.storage.lmdb.IxMap;
import net.ripe.rpki.validator3.storage.lmdb.MultIxMap;
import net.ripe.rpki.validator3.storage.lmdb.Tx;
import net.ripe.rpki.validator3.storage.stores.RpkiObjectStore;
import net.ripe.rpki.validator3.storage.stores.TrustAnchorStore;
import net.ripe.rpki.validator3.storage.stores.ValidationRunStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * TODO Implement missing methods here
 */
@Component
public class LmdbValidationRuns implements ValidationRunStore {

    private static final String RPKI_VALIDATION_RUNS = "validation-runs";
    private static final String CT_RPKI_VALIDATION_RUNS = "ct-validation-runs";
    private static final String RS_RPKI_VALIDATION_RUNS = "rs-validation-runs";
    private static final String TA_RPKI_VALIDATION_RUNS = "ta-validation-runs";
    private static final String LAST_RUNS = "last-runs";
    private static final String RPKI_VALIDATION_RUNS_TO_RPKI_OBJECTS = "validation-runs-to-trust-anchors";
    private static final String RPKI_RPKI_OBJECTS_TO_VALIDATION_RUNS = "trust-anchors-to-validation-runs";
    private static final String BY_TA_INDEX = "by-ta";
    private static final String BY_REPOSITORY_INDEX = "by-repository";

    private final Sequences sequences;

    private final IxMap<SId<RpkiObject>> vr2ro;

    private final MultIxMap<SId<ValidationRun>> ro2vr;

    private final TrustAnchorStore trustAnchorStore;

    private final RpkiObjectStore rpkiObjectStore;

    private final IxMap<CertificateTreeValidationRun> ctIxMap;
    private final IxMap<RsyncRepositoryValidationRun> repoIxMap;
    private final IxMap<TrustAnchorValidationRun> taIxMap;

    private final Map<String, IxMap<? extends ValidationRun>> maps = new HashMap<>();

    @Autowired
    public LmdbValidationRuns(Lmdb lmdb, TrustAnchorStore trustAnchorStore, RpkiObjectStore rpkiObjectStore) {
        this.trustAnchorStore = trustAnchorStore;
        this.rpkiObjectStore = rpkiObjectStore;

        sequences = new Sequences(lmdb);

        ctIxMap = new IxMap<>(lmdb.getEnv(), CT_RPKI_VALIDATION_RUNS, new FSTCoder<>(),
                ImmutableMap.of(BY_TA_INDEX, vr -> Key.keys(vr.getTrustAnchor().key())));

        taIxMap = new IxMap<>(lmdb.getEnv(), TA_RPKI_VALIDATION_RUNS, new FSTCoder<>(),
                ImmutableMap.of(BY_TA_INDEX, vr -> Key.keys(vr.getTrustAnchor().key())));

        repoIxMap = new IxMap<>(lmdb.getEnv(), RS_RPKI_VALIDATION_RUNS, new FSTCoder<>());

        maps.put(CertificateTreeValidationRun.TYPE, ctIxMap);
        maps.put(TrustAnchorValidationRun.TYPE, taIxMap);
        maps.put(RpkiRepositoryValidationRun.TYPE, repoIxMap);

        vr2ro = new IxMap<>(lmdb.getEnv(), RPKI_VALIDATION_RUNS_TO_RPKI_OBJECTS, new FSTCoder<>());
        ro2vr = new MultIxMap<>(lmdb.getEnv(), RPKI_RPKI_OBJECTS_TO_VALIDATION_RUNS, new FSTCoder<>());

        this.rpkiObjectStore.onDelete((tx, roKey) -> {
            ro2vr.get(tx, roKey).forEach(vrId -> {
                Stream.of(ctIxMap, repoIxMap).forEach(ixMap -> ixMap.delete(tx, vrId.key()));
                vr2ro.delete(tx, vrId.key());
            });
            ro2vr.delete(tx, roKey);
        });
        this.trustAnchorStore.onDelete(this::removeAllForTrustAnchor);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends ValidationRun> void add(Tx.Write tx, T vr) {
        final SId<ValidationRun> vrId = SId.of(sequences.next(tx, RPKI_VALIDATION_RUNS + ":pk"));
        vr.setId(vrId);
        pickIxMap(vr.getType()).put(tx, vr.key(), vr);
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
        return pickIxMap(type).values(tx);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends ValidationRun> List<T> findLatestSuccessful(Tx.Read tx, Class<T> type) {
        // TODO Implement using the 'by-completed-at' index with filtering on 'isSuccessful()'
//
//        // TODO Compare it with the original, it's pretty hard to say if
//        //  it's going to return the same result
//        return (List<T>) Stream.of(
//                TrustAnchorValidationRun.TYPE,
//                CertificateTreeValidationRun.TYPE,
//                RpkiRepositoryValidationRun.TYPE)
//                .map(vrType -> lastRunMax.get(tx, Key.of(vrType).concat(Key.of("-successful")))
//                        .flatMap(pk -> maps.get(vrType).get(tx, pk.key())))
//                .filter(Optional::isPresent)
//                .map(Optional::get)
//                .collect(Collectors.toList());

        return Collections.emptyList();
    }

    @Override
    public Optional<TrustAnchorValidationRun> findLatestCompletedForTrustAnchor(Tx.Read tx, TrustAnchor trustAnchor) {
        return taIxMap.getByIndex(BY_TA_INDEX, tx, trustAnchor.key()).stream()
                .filter(vr -> vr.getCompletedAt() != null)
                .max(Comparator.comparing(ValidationRun::getCompletedAt));
    }

    @Override
    public void removeAllForTrustAnchor(Tx.Write tx, TrustAnchor trustAnchor) {
        removeAllForTrustAnchor(tx, trustAnchor.key());
    }

    public void removeAllForTrustAnchor(Tx.Write tx, Key trustAnchorKey) {
        Stream.of(taIxMap, ctIxMap).forEach(ixMap ->
                ixMap.getPkByIndex(BY_TA_INDEX, tx, trustAnchorKey)
                        .forEach(pk -> ixMap.delete(tx, pk)));
    }

    @Override
    public void removeAllForRpkiRepository(Tx.Write tx, RpkiRepository repository) {
        repoIxMap.getPkByIndex(BY_REPOSITORY_INDEX, tx, repository.key())
                .forEach(pk -> repoIxMap.delete(tx, pk));
    }

    @Override
    public long removeOldValidationRuns(Tx.Write tx, Instant completedBefore) {
        // TODO Don't delete the most recent one!

        return 0;
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
    public void associate(Tx.Write writeTx, RsyncRepositoryValidationRun validationRun, RpkiRepository r) {
        // TODO Implement
    }

    @Override
    public void associate(Tx.Write writeTx, RpkiRepositoryValidationRun validationRun, RpkiObject rpkiObject) {
        associateVrAndRo(writeTx, validationRun, rpkiObject);
    }

    private void associateVrAndRo(Tx.Write writeTx, ValidationRun validationRun, RpkiObject rpkiObject) {
        vr2ro.put(writeTx, validationRun.key(), rpkiObject.getId());
        ro2vr.put(writeTx, rpkiObject.key(), validationRun.getId());
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
