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
import net.ripe.rpki.validator3.domain.constraints.ValidLocationURI;
import net.ripe.rpki.validator3.storage.FSTCoder;
import net.ripe.rpki.validator3.storage.Lmdb;
import net.ripe.rpki.validator3.storage.data.RpkiRepository;
import net.ripe.rpki.validator3.storage.data.TrustAnchor;
import net.ripe.rpki.validator3.storage.lmdb.IxMap;
import net.ripe.rpki.validator3.storage.lmdb.Key;
import net.ripe.rpki.validator3.storage.lmdb.Ref;
import net.ripe.rpki.validator3.storage.lmdb.Tx;
import net.ripe.rpki.validator3.storage.stores.RpkiRepostioryStore;
import net.ripe.rpki.validator3.util.Rsync;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.validation.constraints.NotNull;
import java.net.URI;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
@Slf4j
public class LmdbRpkiRepostiories implements RpkiRepostioryStore {

    private static final String RPKI_REPOSITORIES = "rpki-repositories";
    private static final String BY_URI = "by-uri";
    private static final String BY_TA = "by-ta";

    private final IxMap<RpkiRepository> ixMap;
    private final Sequences sequences;

    @Autowired
    public LmdbRpkiRepostiories(Lmdb lmdb) {
        ixMap = new IxMap<>(
                lmdb.getEnv(),
                RPKI_REPOSITORIES,
                new FSTCoder<>(),
                ImmutableMap.of(
                        BY_URI, r -> Key.keys(Key.of(r.getLocationUri())),
                        BY_TA, r -> r.getTrustAnchors().stream().map(ta -> ta.getId()).collect(Collectors.toSet())
                )
        );
        sequences = new Sequences(lmdb);
    }


    @Override
    public RpkiRepository register(Tx.Write tx, TrustAnchor trustAnchor, String uri, RpkiRepository.Type type) {
        log.info("Registering repository {} of type {}", uri, type);
        final Optional<RpkiRepository> existing = findByURI(tx, uri);
        final RpkiRepository registered;
        if (existing.isPresent()) {
            registered = existing.get();
            registered.addTrustAnchor(trustAnchor);
        } else {
            registered = new RpkiRepository(trustAnchor, uri, type);
            final Key primaryKey = Key.of(sequences.next(tx, RPKI_REPOSITORIES + ":pk"));
            registered.setId(primaryKey);
        }

        if (type == RpkiRepository.Type.RSYNC && registered.getType() == RpkiRepository.Type.RSYNC_PREFETCH) {
            registered.setType(RpkiRepository.Type.RSYNC);
        }

        if (registered.getType() == RpkiRepository.Type.RSYNC) {
            findRsyncParentRepository(tx, uri).ifPresent(parent -> {
                registered.setParentRepository(Ref.of(tx, ixMap, parent.getId()));
                if (parent.isDownloaded()) {
                    registered.setDownloaded(parent.getLastDownloadedAt());
                }
            });
        }
        ixMap.put(tx, registered.getId(), registered);
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
    public Optional<RpkiRepository> findByURI(Tx.Read tx, String uri) {
        return ixMap.getByIndex(BY_URI, tx, Key.of(uri)).stream().findFirst();
    }

    @Override
    public Optional<RpkiRepository> get(Tx.Read tx, Key id) {
        return ixMap.get(tx, id);
    }

    @Override
    public Stream<RpkiRepository> findAll(Tx.Read tx, RpkiRepository.Status optionalStatus, Long taId,
                                          boolean hideChildrenOfDownloadedParent,
                                          SearchTerm searchTerm, Sorting sorting, Paging paging) {
        return pagedStream(
                sortedStream(
                        filteredStream(tx, optionalStatus, taId, hideChildrenOfDownloadedParent, searchTerm),
                        sorting),
                paging);
    }

    public Stream<RpkiRepository> filteredStream(Tx.Read tx,
                                                 RpkiRepository.Status optionalStatus,
                                                 Long taId, boolean hideChildrenOfDownloadedParent,
                                                 SearchTerm searchTerm) {
        Stream<RpkiRepository> stream = taId != null ?
                ixMap.getByIndex(BY_TA, tx, Key.of(taId)).stream() :
                ixMap.values(tx).stream();

        if (optionalStatus != null) {
            stream = stream.filter(r -> r.getStatus() == optionalStatus);
        }

        if (searchTerm != null) {
            final String stringTerm = searchTerm.asString().toLowerCase();
            stream = stream.filter(r ->
                    r.getRrdpNotifyUri().toLowerCase().contains(stringTerm) ||
                            r.getStatus().toString().toLowerCase().contains(stringTerm));
        }

        if (hideChildrenOfDownloadedParent) {
            stream = stream.filter(r -> {
                final Ref<RpkiRepository> parentRef = r.getParentRepository();
                final Optional<RpkiRepository> parent = parentRef.getIx().get(tx, parentRef.getKey());
                return !parent.isPresent() ||
                        parent.get().getStatus() == RpkiRepository.Status.FAILED &&
                                parent.get().getLastDownloadedAt() == null;
            });
        }
        return stream;
    }

    private Stream<RpkiRepository> sortedStream(Stream<RpkiRepository> stream, Sorting sorting) {
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

        stream = stream.sorted(comparator);
        return sorting.getDirection() == Sorting.Direction.DESC ?
                stream.sorted(comparator) :
                stream.sorted(comparator.reversed());
    }

    private Stream<RpkiRepository> pagedStream(Stream<RpkiRepository> stream, Paging paging) {
        if (paging != null) {
            return stream.skip(paging.getStartFrom()).limit(paging.getPageSize());
        }
        return stream;
    }

    @Override
    public long countAll(Tx.Read tx, RpkiRepository.Status optionalStatus, Long taId,
                         boolean hideChildrenOfDownloadedParent, SearchTerm searchTerm) {
        return filteredStream(tx, optionalStatus, taId, hideChildrenOfDownloadedParent, searchTerm).count();
    }

    @Override
    public Stream<RpkiRepository> findAll(Tx.Read tx, RpkiRepository.Status optionalStatus, Long taId) {
        final Stream<RpkiRepository> all = findAll(tx, taId);
        return optionalStatus == null ? all : all.filter(r -> r.getStatus() == optionalStatus);
    }

    @Override
    public Stream<RpkiRepository> findAll(Tx.Read tx, Long taId) {
        return ixMap.getByIndex(BY_TA, tx, Key.of(taId)).stream();
    }

    @Override
    public Map<RpkiRepository.Status, Long> countByStatus(Tx.Read tx, Long taId, boolean hideChildrenOfDownloadedParent) {
        return findAll(tx, null, taId, hideChildrenOfDownloadedParent, null, null, null)
                .collect(Collectors.groupingBy(r -> r.getStatus(), Collectors.counting()));
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
    public void removeAllForTrustAnchor(Tx.Write tx, TrustAnchor trustAnchor) {
        ixMap.getByIndexPk(BY_TA, tx, trustAnchor.getId())
                .forEach(pk -> ixMap.delete(tx, pk));
    }

    @Override
    public void remove(Tx.Write tx, Key key) {
        ixMap.delete(tx, key);
    }
}
