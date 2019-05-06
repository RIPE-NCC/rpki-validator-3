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
import net.ripe.rpki.validator3.storage.Bytes;
import net.ripe.rpki.validator3.storage.data.Key;
import net.ripe.rpki.validator3.storage.data.RpkiObject;
import net.ripe.rpki.validator3.storage.data.RpkiRepository;
import net.ripe.rpki.validator3.storage.data.TrustAnchor;
import net.ripe.rpki.validator3.storage.data.validation.CertificateTreeValidationRun;
import net.ripe.rpki.validator3.storage.data.validation.RpkiRepositoryValidationRun;
import net.ripe.rpki.validator3.storage.data.validation.RrdpRepositoryValidationRun;
import net.ripe.rpki.validator3.storage.data.validation.RsyncRepositoryValidationRun;
import net.ripe.rpki.validator3.storage.data.validation.TrustAnchorValidationRun;
import net.ripe.rpki.validator3.storage.data.validation.ValidationCheck;
import net.ripe.rpki.validator3.storage.data.validation.ValidationRun;
import net.ripe.rpki.validator3.storage.encoding.Coder;
import net.ripe.rpki.validator3.storage.lmdb.IxMap;
import net.ripe.rpki.validator3.storage.lmdb.Lmdb;
import net.ripe.rpki.validator3.storage.lmdb.MultIxMap;
import net.ripe.rpki.validator3.storage.lmdb.Tx;
import net.ripe.rpki.validator3.storage.stores.RpkiObjectStore;
import net.ripe.rpki.validator3.storage.stores.RpkiRepositoryStore;
import net.ripe.rpki.validator3.storage.stores.TrustAnchorStore;
import net.ripe.rpki.validator3.storage.stores.ValidationRunStore;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

@Component
@Slf4j
public class LmdbValidationRuns implements ValidationRunStore {

    private static final String RPKI_VALIDATION_RUNS = "validation-runs";
    private static final String CT_RPKI_VALIDATION_RUNS = "certificate-tree-validation-runs";
    private static final String RS_RPKI_VALIDATION_RUNS = "rsync-repository-validation-runs";
    private static final String RR_RPKI_VALIDATION_RUNS = "rrdp-repository-validation-runs";
    private static final String TA_RPKI_VALIDATION_RUNS = "trust-anchor-validation-runs";
    private static final String VALIDATION_RUNS_TO_RPKI_OBJECTS = "validation-runs-to-rpki-objects";
    private static final String VALIDATION_RUNS_TO_RPKI_REPOSITORIES = "validation-runs-to-repositories";
    private static final String BY_TA_INDEX = "by-ta";
    private static final String BY_COMPLETED_AT_INDEX = "by-completed-at";

    private MultIxMap<Key> vr2ro;
    private IxMap<Key> vr2repo;

    private IxMap<CertificateTreeValidationRun> ctIxMap;
    private IxMap<RsyncRepositoryValidationRun> rsIxMap;
    private IxMap<RrdpRepositoryValidationRun> rrIxMap;
    private IxMap<TrustAnchorValidationRun> taIxMap;

    private final Map<String, IxMap<? extends ValidationRun>> maps = new HashMap<>();

    private final RpkiObjectStore rpkiObjectStore;
    private final RpkiRepositoryStore rpkiRepositoryStore;

    private final Sequences sequences;

    public LmdbValidationRuns(RpkiObjectStore rpkiObjectStore,
                              @Lazy TrustAnchorStore trustAnchorStore,
                              RpkiRepositoryStore rpkiRepositoryStore,
                              Sequences sequences,
                              Lmdb lmdb) {
        this.rpkiObjectStore = rpkiObjectStore;
        this.rpkiRepositoryStore = rpkiRepositoryStore;
        this.sequences = sequences;

        ctIxMap = lmdb.createIxMap(
                CT_RPKI_VALIDATION_RUNS,
                ImmutableMap.of(BY_TA_INDEX, vr -> Key.keys(vr.getTrustAnchor().key()),
                        BY_COMPLETED_AT_INDEX, this::completedAtIndexKeys),
                CertificateTreeValidationRun.class);

        taIxMap = lmdb.createIxMap(
                TA_RPKI_VALIDATION_RUNS,
                ImmutableMap.of(BY_TA_INDEX, vr -> Key.keys(vr.getTrustAnchor().key()),
                        BY_COMPLETED_AT_INDEX, this::completedAtIndexKeys),
                TrustAnchorValidationRun.class);

        rsIxMap = lmdb.createIxMap(
                RS_RPKI_VALIDATION_RUNS,
                ImmutableMap.of(BY_COMPLETED_AT_INDEX, this::completedAtIndexKeys),
                RsyncRepositoryValidationRun.class);

        rrIxMap = lmdb.createIxMap(
                RR_RPKI_VALIDATION_RUNS,
                ImmutableMap.of(BY_COMPLETED_AT_INDEX, this::completedAtIndexKeys),
                RrdpRepositoryValidationRun.class);

        maps.put(CertificateTreeValidationRun.TYPE, ctIxMap);
        maps.put(TrustAnchorValidationRun.TYPE, taIxMap);
        maps.put(RsyncRepositoryValidationRun.TYPE, rsIxMap);
        maps.put(RrdpRepositoryValidationRun.TYPE, rrIxMap);

        final Coder<Key> keyCoder = new Coder<Key>() {
            @Override
            public ByteBuffer toBytes(Key key) {
                return key.toByteBuffer();
            }

            @Override
            public Key fromBytes(ByteBuffer bb) {
                return new Key(bb);
            }
        };
        vr2ro = lmdb.createMultIxMap(VALIDATION_RUNS_TO_RPKI_OBJECTS, keyCoder);
        vr2repo = lmdb.createIxMap(VALIDATION_RUNS_TO_RPKI_REPOSITORIES, Collections.emptyMap(), keyCoder);

        trustAnchorStore.onDelete(this::removeAllForTrustAnchor);

        maps.values().forEach(ixMap ->
                ixMap.onDelete((tx, vrKey) -> {
                    vr2ro.delete(tx, vrKey);
                    vr2repo.delete(tx, vrKey);
                }));
    }

    private Set<Key> completedAtIndexKeys(ValidationRun vr) {
        Instant completedAt = vr.getCompletedAt();
        return completedAt != null ? Key.keys(Key.of(completedAt.toEpochMilli())) : Collections.emptySet();
    }

    @Override
    public <T extends ValidationRun> T add(Tx.Write tx, T vr) {
        vr.setId(Key.of(sequences.next(tx, RPKI_VALIDATION_RUNS + ":pk")));
        pickIxMap(vr.getType()).put(tx, vr.key(), vr);
        return vr;
    }

    @Override
    public <T extends ValidationRun> void update(Tx.Write tx, T vr) {
        vr.setUpdatedAt(Instant.now());
        pickIxMap(vr.getType()).put(tx, vr.key(), vr);
    }

    @Override
    public <T extends ValidationRun> Optional<T> get(Tx.Read tx, Class<T> type, long id) {
        return pickIxMaps(type).stream()
                .map(ixMap -> ixMap.get(tx, Key.of(id)))
                .filter(Optional::isPresent)
                .findFirst()
                .map(validationRun -> (T) validationRun.get());
    }

    @Override
    public <T extends ValidationRun> List<T> findAll(Tx.Read tx, Class<T> type) {
        final List<T> result = new ArrayList<>();
        pickIxMaps(type).forEach(ixMap ->
                ixMap.forEach(tx, (k, bb) ->
                        result.add((T) ixMap.toValue(bb))));
        return result;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends ValidationRun> List<T> findLatestSuccessful(Tx.Read tx, Class<T> type) {
        final List<T> result = new ArrayList<>();
        List<IxMap<ValidationRun>> ixMaps = pickIxMaps(type);
        ixMaps.forEach(ixMap ->
                ixMap.getByIndexMax(BY_COMPLETED_AT_INDEX, tx, ValidationRun::isSucceeded)
                        .forEach((k, v) -> result.add((T) v)));
        return result;
    }

    @Override
    public Optional<CertificateTreeValidationRun> findLatestSuccessfulCaTreeValidationRun(Tx.Read tx, TrustAnchor trustAnchor) {
        return ctIxMap.getByIndexMax(BY_COMPLETED_AT_INDEX, tx, vr ->
                vr.isSucceeded() && trustAnchor.key().equals(vr.getTrustAnchor().key())).values().stream().findFirst();
    }

    @Override
    public Optional<TrustAnchorValidationRun> findLatestCompletedForTrustAnchor(Tx.Read tx, TrustAnchor trustAnchor) {
        return taIxMap.getByIndexMax(BY_COMPLETED_AT_INDEX, tx,
                vr -> trustAnchor.key().equals(vr.getTrustAnchor().key())).values().stream().findFirst();
    }

    @Override
    public Optional<CertificateTreeValidationRun> findLatestCaTreeValidationRun(Tx.Read tx, TrustAnchor trustAnchor) {
        return ctIxMap.getByIndexMax(BY_COMPLETED_AT_INDEX, tx,
                vr -> trustAnchor.key().equals(vr.getTrustAnchor().key())).values().stream().findFirst();
    }

    private void removeAllForTrustAnchor(Tx.Write tx, Key trustAnchorKey) {
        Stream.of(taIxMap, ctIxMap).forEach(ixMap ->
                ixMap.getPkByIndex(BY_TA_INDEX, tx, trustAnchorKey)
                        .forEach(pk -> ixMap.delete(tx, pk)));
    }

    @Override
    public int removeOldValidationRuns(Tx.Write tx, Instant completedBefore) {
        AtomicInteger count = new AtomicInteger(0);
        maps.forEach((type, ixMap) -> {
            // Don't delete the most recent one successful
            final Set<Key> latestSuccessfulKeys = ixMap.getByIndexMax(BY_COMPLETED_AT_INDEX, tx, ValidationRun::isSucceeded).keySet();
            final Set<Key> toDelete = new HashSet<>();
            ixMap.forEach(tx, (k, bb) -> {
                ValidationRun validationRun = ixMap.toValue(bb);
                boolean deleteIt = false;
                if (validationRun.getCompletedAt() != null) {
                    if (validationRun.getCompletedAt().isBefore(completedBefore)) {
                        deleteIt = true;
                    }
                } else {
                    if (validationRun.getUpdatedAt() != null && validationRun.getUpdatedAt().isBefore(completedBefore)) {
                        deleteIt = true;
                    } else if (validationRun.getCreatedAt().isBefore(completedBefore)) {
                        deleteIt = true;
                    }
                }
                if (deleteIt && !latestSuccessfulKeys.contains(k)) {
                    toDelete.add(k);
                }
            });
            toDelete.forEach(pk -> ixMap.delete(tx, pk));
            count.addAndGet(toDelete.size());
        });
        return count.get();
    }

    @Override
    public int removeOrphanValidationRunAssociations(Tx.Write tx) {
        final Set<Key> roKeys = rpkiObjectStore.keys(tx);
        final Set<Key> repoKeys = rpkiRepositoryStore.keys(tx);
        return vr2ro.deleteIf(tx, (k, bb) -> !roKeys.contains(Key.of(Bytes.toBytes(bb)))) +
                vr2repo.deleteIf(tx, (k1, bb) -> !repoKeys.contains(Key.of(Bytes.toBytes(bb))));
    }

    @Override
    public void delete(Tx.Write tx, ValidationRun vr) {
        pickIxMap(vr.getType()).delete(tx, vr.key());
    }

    @Override
    public Stream<ValidationCheck> findValidationChecksForValidationRun(Tx.Read tx, long trustAnchorId, Paging paging, SearchTerm searchTerm, Sorting sorting) {
        return applyPaging(paging,
                applySorting(sorting,
                        applySearchTerm(searchTerm, validationCheckForTaStreams(tx, trustAnchorId))));
    }

    @Override
    public int countValidationChecksForValidationRun(Tx.Read tx, long trustAnchorId, SearchTerm searchTerm) {
        return (int) applySearchTerm(searchTerm, validationCheckForTaStreams(tx, trustAnchorId)).count();
    }

    private Stream<ValidationCheck> validationCheckForTaStreams(Tx.Read tx, long trustAnchorId) {
        Stream<ValidationCheck> taChecks = taIxMap.getByIndexMax(BY_COMPLETED_AT_INDEX, tx,
                vr -> trustAnchorId == vr.getTrustAnchor().key().asLong())
                .values()
                .stream()
                .findFirst()
                .map(ValidationRun::getValidationChecks)
                .orElse(Collections.emptyList())
                .stream();

        Stream<ValidationCheck> ctChecks = ctIxMap.getByIndexMax(BY_COMPLETED_AT_INDEX, tx,
                vr -> trustAnchorId == vr.getTrustAnchor().key().asLong())
                .values()
                .stream()
                .findFirst()
                .map(ValidationRun::getValidationChecks)
                .orElse(Collections.emptyList())
                .stream();

        return Stream.concat(taChecks, ctChecks);
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
    public void associate(Tx.Write tx, RpkiRepositoryValidationRun validationRun, RpkiObject rpkiObject) {
        vr2ro.put(tx, validationRun.key(), rpkiObject.key());
    }

    @Override
    public void associateRpkiObjectKey(Tx.Write tx, CertificateTreeValidationRun validationRun, Key rpkiObjectKey) {
        vr2ro.put(tx, validationRun.key(), rpkiObjectKey);
    }

    @Override
    public void associate(Tx.Write tx, RsyncRepositoryValidationRun validationRun, RpkiRepository rpkiRepository) {
        vr2repo.put(tx, validationRun.key(), rpkiRepository.key());
    }

    @Override
    public Set<Key> findAssociatedPks(Tx.Read tx, CertificateTreeValidationRun validationRun) {
        return new HashSet<>(vr2ro.get(tx, validationRun.key()));
    }

    @Override
    public Stream<Pair<CertificateTreeValidationRun, RpkiObject>> findCurrentlyValidated(Tx.Read tx, RpkiObject.Type type) {
        final Set<Key> byType = rpkiObjectStore.getPkByType(tx, type);
        return findLatestSuccessful(tx, CertificateTreeValidationRun.class)
                .stream()
                .flatMap(ct -> vr2ro.get(tx, ct.key())
                        .stream()
                        .filter(byType::contains)
                        .map(roKey -> rpkiObjectStore.get(tx, roKey))
                        .filter(Optional::isPresent)
                        .map(Optional::get)
                        .filter(ro -> ro.getType() == type)
                        .map(ro -> Pair.of(ct, ro)));
    }

    @Override
    public void clear(Tx.Write tx) {
        Stream.of(vr2ro, vr2repo, ctIxMap, taIxMap, rsIxMap, rrIxMap)
                .forEach(ixMap -> ixMap.clear(tx));
    }

    @Override
    public int getObjectCount(Tx.Read tx, ValidationRun validationRun) {
        return vr2ro.count(tx, validationRun.key());
    }

    @SuppressWarnings("unchecked")
    private <T extends ValidationRun> IxMap<T> pickIxMap(String vrType) {
        final IxMap<? extends ValidationRun> ixMap = maps.get(vrType);
        if (ixMap == null) {
            throw new RuntimeException("Oops, you are looking for the " + vrType + " which is not here.");
        }
        return (IxMap<T>) ixMap;
    }

    // TODO Come up with something better than this horror
    private List<IxMap<ValidationRun>> pickIxMaps(Class<? extends ValidationRun> c) {
        List<IxMap<ValidationRun>> ixMaps = new ArrayList<>();
        if (c.isAssignableFrom(CertificateTreeValidationRun.class)) {
            ixMaps.add(pickIxMap(CertificateTreeValidationRun.TYPE));
        }
        if (c.isAssignableFrom(TrustAnchorValidationRun.class)) {
            ixMaps.add(pickIxMap(TrustAnchorValidationRun.TYPE));
        }
        if (c.isAssignableFrom(RrdpRepositoryValidationRun.class)) {
            ixMaps.add(pickIxMap(RrdpRepositoryValidationRun.TYPE));
        }
        if (c.isAssignableFrom(RsyncRepositoryValidationRun.class)) {
            ixMaps.add(pickIxMap(RsyncRepositoryValidationRun.TYPE));
        }
        return ixMaps;
    }

}
