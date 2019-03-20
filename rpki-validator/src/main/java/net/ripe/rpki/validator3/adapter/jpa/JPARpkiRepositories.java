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
package net.ripe.rpki.validator3.adapter.jpa;

import com.querydsl.core.Tuple;
import com.querydsl.core.types.Expression;
import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.jpa.impl.JPAQuery;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.validator3.api.Paging;
import net.ripe.rpki.validator3.api.SearchTerm;
import net.ripe.rpki.validator3.api.Sorting;
import net.ripe.rpki.validator3.background.ValidationScheduler;
import net.ripe.rpki.validator3.domain.RpkiRepositories;
import net.ripe.rpki.validator3.domain.RpkiRepository;
import net.ripe.rpki.validator3.domain.TrustAnchor;
import net.ripe.rpki.validator3.domain.TrustAnchors;
import net.ripe.rpki.validator3.domain.ValidationRuns;
import net.ripe.rpki.validator3.domain.constraints.ValidLocationURI;
import net.ripe.rpki.validator3.util.Rsync;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import javax.transaction.Transactional;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.net.URI;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static net.ripe.rpki.validator3.domain.querydsl.QRpkiRepository.rpkiRepository;

@Repository
@Transactional(Transactional.TxType.REQUIRED)
@Slf4j
public class JPARpkiRepositories extends JPARepository<RpkiRepository> implements RpkiRepositories {
    private final ValidationScheduler validationScheduler;
    private final ValidationRuns validationRuns;
    private final TrustAnchors trustAnchors;

    @Autowired
    public JPARpkiRepositories(ValidationScheduler validationScheduler, ValidationRuns validationRuns, TrustAnchors trustAnchors) {
        super(rpkiRepository);
        this.validationScheduler = validationScheduler;
        this.validationRuns = validationRuns;
        this.trustAnchors = trustAnchors;
    }

    @Override
    public RpkiRepository register(@NotNull @Valid TrustAnchor trustAnchor, @NotNull @ValidLocationURI String uri, RpkiRepository.Type type) {
        log.info("Registering repository {} of type {}", uri, type);
        RpkiRepository result = findByURI(uri).orElseGet(() -> {
            RpkiRepository repository = new RpkiRepository(trustAnchor, uri, type);
            entityManager.persist(repository);
            return repository;
        });
        result.addTrustAnchor(trustAnchor);
        if (type == RpkiRepository.Type.RSYNC && result.getType() == RpkiRepository.Type.RSYNC_PREFETCH) {
            result.setType(RpkiRepository.Type.RSYNC);
        }

        if (result.getType() == RpkiRepository.Type.RSYNC) {
            RpkiRepository foundParent = findRsyncParentRepository(uri);
            if (foundParent != null) {
                log.info("Found a parent {} to the repository {}", foundParent, uri);
                result.setParentRepository(foundParent);
                if (foundParent.isDownloaded()) {
                    result.setDownloaded(foundParent.getLastDownloadedAt());
                }
            }
        }
        return result;
    }

    private RpkiRepository findRsyncParentRepository(@NotNull @ValidLocationURI String uri) {
        return Rsync.generateCandidateParentUris(URI.create(uri)).stream()
                .map(parentURI -> select().where(rpkiRepository.rsyncRepositoryUri.eq(parentURI.toASCIIString())).fetchFirst())
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    @Override
    public Optional<RpkiRepository> findByURI(@NotNull @ValidLocationURI String uri) {
        String normalized = URI.create(uri).normalize().toASCIIString();
        return Optional.ofNullable(select().where(
            rpkiRepository.rrdpNotifyUri.eq(normalized).or(rpkiRepository.rsyncRepositoryUri.eq(normalized))
        ).fetchFirst());
    }

    @Override
    public Stream<RpkiRepository> findAll(RpkiRepository.Status optionalStatus, Long taId, boolean hideChildrenOfDownloadedParent, SearchTerm searchTerm, Sorting sorting, Paging paging) {
        JPAQuery<RpkiRepository> query = applyFilters(select(), optionalStatus, taId, hideChildrenOfDownloadedParent, searchTerm);

        query.orderBy(toOrderSpecifier(sorting));
        applyPaging(query, paging);

        return stream(query);
    }

    @Override
    public long countAll(RpkiRepository.Status optionalStatus, Long taId, boolean hideChildrenOfDownloadedParent, SearchTerm searchTerm) {
        JPAQuery<RpkiRepository> query = applyFilters(select(), optionalStatus, taId, hideChildrenOfDownloadedParent, searchTerm);
        return query.fetchCount();
    }

    @Override
    public Map<RpkiRepository.Status, Long> countByStatus(Long taId, boolean hideChildrenOfDownloadedParent) {
        JPAQuery<RpkiRepository> query = applyFilters(select(), null, taId, hideChildrenOfDownloadedParent, null);

        Stream<Tuple> counts = stream(query.groupBy(rpkiRepository.status).select(rpkiRepository.status, rpkiRepository.count()));
        return counts.collect(Collectors.toMap(
            tuple -> tuple.get(0, RpkiRepository.Status.class),
            tuple -> tuple.get(1, Long.class)
        ));
    }

    @Override
    public Stream<RpkiRepository> findRsyncRepositories() {
        return stream(
            select()
                .where(rpkiRepository.type.in(RpkiRepository.Type.RSYNC, RpkiRepository.Type.RSYNC_PREFETCH))
                .orderBy(rpkiRepository.rsyncRepositoryUri.asc(), rpkiRepository.id.asc())
        );
    }

    @Override
    public Stream<RpkiRepository> findRrdpRepositories() {
        return stream(
                select()
                        .where(rpkiRepository.type.in(RpkiRepository.Type.RRDP, RpkiRepository.Type.RSYNC_PREFETCH))
                        .orderBy(rpkiRepository.rsyncRepositoryUri.asc(), rpkiRepository.id.asc())
        );
    }

    @Override
    public void removeAllForTrustAnchor(TrustAnchor trustAnchor) {
        for (RpkiRepository repository : select().where(rpkiRepository.trustAnchors.contains(trustAnchor)).fetch()) {
            repository.removeTrustAnchor(trustAnchor);
            if (repository.getTrustAnchors().isEmpty()) {
                if (repository.getType() == RpkiRepository.Type.RRDP) {
                    validationScheduler.removeRpkiRepository(repository);
                }
                validationRuns.removeAllForRpkiRepository(repository);
                entityManager.remove(repository);
            }
        }
    }

    private JPAQuery<RpkiRepository> applyFilters(JPAQuery<RpkiRepository> query, RpkiRepository.Status optionalStatus, Long taId, boolean hideChildrenOfDownloadedParent, SearchTerm searchTerm) {
        if (optionalStatus != null) {
            query.where(rpkiRepository.status.eq(optionalStatus));
        }
        if (taId != null) {
            query.where(rpkiRepository.trustAnchors.any().id.eq(taId));
        }
        if (hideChildrenOfDownloadedParent) {
            // Keep repository if it is not a child or (the parent is failed and has never been successfully downloaded).
            query.leftJoin(rpkiRepository.parentRepository).where(
                rpkiRepository.parentRepository.isNull().or(
                    rpkiRepository.parentRepository.status.eq(RpkiRepository.Status.FAILED).and(
                        rpkiRepository.parentRepository.lastDownloadedAt.isNull()
                    )
                )
            );
        }
        if (searchTerm != null) {
            query.where(
                rpkiRepository.rsyncRepositoryUri.likeIgnoreCase("%" + searchTerm.asString() + "%").or(
                    rpkiRepository.rrdpNotifyUri.likeIgnoreCase("%" + searchTerm.asString() + "%")
                ).or(
                    rpkiRepository.status.stringValue().likeIgnoreCase("%" + searchTerm.asString() + "%")
                ));
        }
        return query;
    }

    private OrderSpecifier<?> toOrderSpecifier(Sorting sorting) {
        if (sorting == null) {
            sorting = Sorting.of(Sorting.By.LOCATION, Sorting.Direction.ASC);
        }

        Expression<? extends Comparable> column;
        switch (sorting.getBy()) {
            case TYPE:
                column = rpkiRepository.type;
                break;
            case STATUS:
                column = rpkiRepository.status;
                break;
            case LASTCHECKED:
                column = rpkiRepository.updatedAt;
                break;
            case LOCATION:
            default:
                column = rpkiRepository.rrdpNotifyUri.coalesce(rpkiRepository.rsyncRepositoryUri);
                break;
        }

        Order order = sorting.getDirection() == Sorting.Direction.DESC ? Order.DESC : Order.ASC;
        return new OrderSpecifier<>(order, column);
    }
}
