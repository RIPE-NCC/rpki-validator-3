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

import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableMap;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.validator3.api.Paging;
import net.ripe.rpki.validator3.api.SearchTerm;
import net.ripe.rpki.validator3.api.Sorting;
import net.ripe.rpki.validator3.api.util.InstantWithoutNanos;
import net.ripe.rpki.validator3.background.ValidationScheduler;
import net.ripe.rpki.validator3.domain.constraints.ValidLocationURI;
import net.ripe.rpki.validator3.storage.IxMap;
import net.ripe.rpki.validator3.storage.Tx;
import net.ripe.rpki.validator3.storage.data.Key;
import net.ripe.rpki.validator3.storage.data.Ref;
import net.ripe.rpki.validator3.storage.data.RpkiRepository;
import net.ripe.rpki.validator3.storage.data.TrustAnchor;
import net.ripe.rpki.validator3.storage.Storage;
import net.ripe.rpki.validator3.storage.stores.GenericStoreImpl;
import net.ripe.rpki.validator3.storage.stores.RpkiRepositories;
import net.ripe.rpki.validator3.storage.stores.TrustAnchors;
import net.ripe.rpki.validator3.util.Rsync;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.validation.constraints.NotNull;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
@Slf4j
public class RpkiRepositoriesStore extends GenericStoreImpl<RpkiRepository> implements RpkiRepositories {

    private static final String RPKI_REPOSITORIES = "rpki-repositories";
    private static final String BY_URI_PREFIX = "by-uri";
    private static final String BY_TA = "by-ta";

    private final IxMap<RpkiRepository> ixMap;
    private final SequencesStore sequences;
    private final ValidationScheduler validationScheduler;

    @Autowired
    public RpkiRepositoriesStore(Storage storage, SequencesStore sequences, ValidationScheduler validationScheduler) {
        this.sequences = sequences;
        this.validationScheduler = validationScheduler;

        ixMap = storage.createIxMap(
                RPKI_REPOSITORIES,
                ImmutableMap.of(
                        BY_URI_PREFIX, this::locationIndex,
                        BY_TA, r -> r.getTrustAnchors().keySet().stream().map(Ref::key).collect(Collectors.toSet())
                ),
                RpkiRepository.class
        );
    }

    private Key uriToKey(String uri) {
        return Key.of(uri.substring(0, Math.min(uri.length(), 100)));
    }

    private Set<Key> locationIndex(RpkiRepository repository) {
        return Stream.of(repository.getRrdpNotifyUri(), repository.getRsyncRepositoryUri())
                .filter(Objects::nonNull)
                // this is to avoid troubles with URLs that are too long and thus key is too long
                .map(this::uriToKey)
                .collect(Collectors.toSet());
    }

    @Override
    public RpkiRepository register(Tx.Write tx, Ref<TrustAnchor> trustAnchorRef, String uri, RpkiRepository.Type type) {
        final Optional<RpkiRepository> existing = findByURI(tx, uri);

        final RpkiRepository registered;
        if (existing.isPresent()) {
            registered = existing.get();
            registered.addTrustAnchor(trustAnchorRef);
        } else {
            log.info("Registering new repository {} of type {}", uri, type);
            registered = new RpkiRepository(trustAnchorRef, uri, type);
            final Key primaryKey = Key.of(sequences.next(tx, RPKI_REPOSITORIES + ":pk"));
            registered.setId(primaryKey);
        }

        if (type == RpkiRepository.Type.RSYNC && registered.getType() == RpkiRepository.Type.RSYNC_PREFETCH) {
            registered.setType(RpkiRepository.Type.RSYNC);
        }

        if (registered.getType() == RpkiRepository.Type.RSYNC) {
            findRsyncParentRepository(tx, uri).ifPresent(parent -> {
                if (parent.isDownloaded()) {
                    registered.setDownloaded(parent.getLastDownloadedAt());
                }
            });
        }
        ixMap.put(tx, registered.key(), registered);
        return registered;
    }

    private Optional<RpkiRepository> findRsyncParentRepository(Tx.Read tx, @NotNull @ValidLocationURI String uri) {
        return Rsync.generateCandidateParentUris(URI.create(uri)).stream()
                .map(parentURI -> findByURI(tx, parentURI.toASCIIString()))
                .filter(Optional::isPresent)
                .findFirst()
                .flatMap(Function.identity());
    }

    @Override
    public void update(Tx.Write tx, RpkiRepository rpkiRepository) {
        rpkiRepository.setUpdatedAt(InstantWithoutNanos.now());
        ixMap.put(tx, rpkiRepository.key(), rpkiRepository);
    }

    @Override
    public Optional<RpkiRepository> findByURI(Tx.Read tx, String uri) {
        return ixMap.getByIndex(BY_URI_PREFIX, tx, uriToKey(uri)).values()
                .stream()
                .filter(r -> uri.equals(r.getRrdpNotifyUri()) || uri.equals(r.getRsyncRepositoryUri()))
                .findFirst();
    }

    @Override
    public Optional<RpkiRepository> get(Tx.Read tx, Key id) {
        return ixMap.get(tx, id);
    }

    @Override
    public Stream<RpkiRepository> findAll(Tx.Read tx, RpkiRepository.Status optionalStatus, Key taId,
                                          boolean hideChildrenOfDownloadedParent,
                                          SearchTerm searchTerm, Sorting sorting, Paging paging) {
        return applyPaged(
                applySorting(
                        applyFiltered(tx, optionalStatus, taId, hideChildrenOfDownloadedParent, searchTerm),
                        sorting),
                paging);
    }

    // TODO Optimize it with forEach
    private Stream<RpkiRepository> applyFiltered(Tx.Read tx,
                                                 RpkiRepository.Status optionalStatus,
                                                 Key taId, boolean hideChildrenOfDownloadedParent,
                                                 SearchTerm searchTerm) {
        Stream<RpkiRepository> stream = taId != null ?
                ixMap.getByIndex(BY_TA, tx, taId).values().stream() :
                ixMap.values(tx).stream();

        if (optionalStatus != null) {
            stream = stream.filter(r -> r.getStatus() == optionalStatus);
        }

        if (searchTerm != null) {
            final String stringTerm = searchTerm.asString().toLowerCase();
            stream = stream.filter(r ->
                    r.getLocationUri() != null && r.getLocationUri().toLowerCase().contains(stringTerm) ||
                    r.getStatus() != null && r.getStatus().toString().toLowerCase().contains(stringTerm));
        }

        if (hideChildrenOfDownloadedParent) {
            stream = stream.filter(r -> {
                if (r.getType() != RpkiRepository.Type.RSYNC) {
                    return true;
                }
                final Optional<RpkiRepository> parent = findRsyncParentRepository(tx, r.getRsyncRepositoryUri());
                return !parent.isPresent() ||
                        parent.get().getStatus() == RpkiRepository.Status.FAILED &&
                                parent.get().getLastDownloadedAt() == null;
            });
        }
        return stream;
    }

    private Stream<RpkiRepository> applySorting(Stream<RpkiRepository> stream, Sorting sorting) {
        if (sorting == null) {
            sorting = Sorting.of(Sorting.By.LOCATION, Sorting.Direction.ASC);
        }
        Comparator<RpkiRepository> comparator;
        switch (sorting.getBy()) {
            case TYPE:
                comparator = Comparator.comparing(RpkiRepository::getType);
                break;
            case STATUS:
                comparator = Comparator.comparing(RpkiRepository::getStatus);
                break;
            case LASTCHECKED:
                comparator = Comparator.comparing(RpkiRepository::getUpdatedAt);
                break;
            case LOCATION:
            default:
                comparator = Comparator.comparing(RpkiRepository::getLocationUri);
                break;
        }
        return stream.sorted(
                sorting.getDirection() == Sorting.Direction.DESC ?
                        comparator :
                        comparator.reversed());
    }

    private Stream<RpkiRepository> applyPaged(Stream<RpkiRepository> stream, Paging paging) {
        if (paging != null) {
            return paging.apply(stream);
        }
        return stream;
    }

    @Override
    public long countAll(Tx.Read tx, RpkiRepository.Status optionalStatus, Key taId,
                         boolean hideChildrenOfDownloadedParent, SearchTerm searchTerm) {
        return applyFiltered(tx, optionalStatus, taId, hideChildrenOfDownloadedParent, searchTerm).count();
    }

    @Override
    public Stream<RpkiRepository> findAll(Tx.Read tx, RpkiRepository.Status optionalStatus, Key taId) {
        final Stream<RpkiRepository> all = findAll(tx, taId);
        return optionalStatus == null ? all : all.filter(r -> r.getStatus() == optionalStatus);
    }

    @Override
    public Stream<RpkiRepository> findAll(Tx.Read tx, Key taId) {
        return ixMap.getByIndex(BY_TA, tx, taId).values().stream();
    }

    @Override
    public Map<RpkiRepository.Status, Long> countByStatus(Tx.Read tx, Key taId, boolean hideChildrenOfDownloadedParent) {
        return findAll(tx, null, taId, hideChildrenOfDownloadedParent, null, null, null)
                .collect(Collectors.groupingBy(RpkiRepository::getStatus, Collectors.counting()));
    }

    @Override
    public Stream<RpkiRepository> findRsyncRepositories(Tx.Read tx) {
        return findRepositoriesByPredicate(tx, r ->
                r.getType() == RpkiRepository.Type.RSYNC ||
                r.getType() == RpkiRepository.Type.RSYNC_PREFETCH);
    }

    @Override
    public Stream<RpkiRepository> findRrdpRepositories(Tx.Read tx) {
        return findRepositoriesByPredicate(tx, r -> r.getType() == RpkiRepository.Type.RRDP);
    }

    private Stream<RpkiRepository> findRepositoriesByPredicate(Tx.Read tx, Predicate<RpkiRepository> p) {
        final List<RpkiRepository> result = new ArrayList<>();
        ixMap.forEach(tx, (k, bb) -> {
            final RpkiRepository r = ixMap.toValue(bb);
            if (p.test(r)) {
                result.add(r);
            }
        });
        return result.stream();
    }

    @Override
    public void removeAllForTrustAnchor(Tx.Write tx, TrustAnchor trustAnchor) {
        final Ref<TrustAnchor> taRef = Ref.unsafe(TrustAnchors.TRUST_ANCHORS, trustAnchor.key());
        ixMap.getByIndex(BY_TA, tx, trustAnchor.key())
                .forEach((pk, rpkiRepository) -> {
                    rpkiRepository.removeTrustAnchor(taRef);
                    if (rpkiRepository.getTrustAnchors().isEmpty()) {
                        if (rpkiRepository.getType() == RpkiRepository.Type.RRDP) {
                            tx.afterCommit(() -> validationScheduler.removeRrdpRpkiRepository(rpkiRepository));
                        }
                        ixMap.delete(tx, pk);
                    } else {
                        ixMap.put(tx, pk, rpkiRepository);
                    }
                });
    }

    @Override
    public void remove(Tx.Write tx, Key key) {
        ixMap.delete(tx, key);
    }

    @Override
    public Collection<RpkiRepository> findByTrustAnchor(Tx.Read tx, Key key) {
        return ixMap.getByIndex(BY_TA, tx, key).values();
    }

    @Override
    public long deleteUnreferencedRepositories(Tx.Write tx, InstantWithoutNanos unreferencedSince) {
        Stream<RpkiRepository> repositories = findRepositoriesByPredicate(tx, Predicates.alwaysTrue());
        AtomicLong counter = new AtomicLong(0);

        repositories
                .sorted(Comparator.comparing(RpkiRepository::getLocationUri).reversed())
                .forEach(rpkiRepository -> {
                    Map<Ref<TrustAnchor>, InstantWithoutNanos> trustAnchors = rpkiRepository.getTrustAnchors();

                    boolean updated = false;
                    Iterator<Map.Entry<Ref<TrustAnchor>, InstantWithoutNanos>> it = trustAnchors.entrySet().iterator();
                    while (it.hasNext()) {
                        Map.Entry<Ref<TrustAnchor>, InstantWithoutNanos> pair = it.next();
                        InstantWithoutNanos lastReferencedAt = pair.getValue();
                        if (lastReferencedAt.isBefore(unreferencedSince)) {
                            it.remove();
                            updated = true;
                        }
                    }

                    if (trustAnchors.isEmpty()) {
                        log.info("removing RPKI repository {} (unreferenced since {})", rpkiRepository.getLocationUri(), unreferencedSince);
                        if (rpkiRepository.getType() == RpkiRepository.Type.RRDP) {
                            tx.afterCommit(() -> validationScheduler.removeRrdpRpkiRepository(rpkiRepository));
                        }
                        ixMap.delete(tx, rpkiRepository.key());

                        counter.incrementAndGet();
                    } else if (updated) {
                        update(tx, rpkiRepository);
                    }
                });
        return counter.get();
    }

    @Override
    protected IxMap<RpkiRepository> ixMap() {
        return ixMap;
    }
}
