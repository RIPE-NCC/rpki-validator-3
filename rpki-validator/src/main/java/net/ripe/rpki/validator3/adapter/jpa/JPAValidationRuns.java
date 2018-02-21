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

import com.querydsl.core.types.ExpressionUtils;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.dsl.ComparableExpression;
import com.querydsl.core.types.dsl.PathBuilder;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.JPQLQuery;
import com.querydsl.jpa.impl.JPAQuery;
import net.ripe.rpki.validator3.api.Paging;
import net.ripe.rpki.validator3.api.Sorting;
import net.ripe.rpki.validator3.api.SearchTerm;
import net.ripe.rpki.validator3.domain.RpkiRepository;
import net.ripe.rpki.validator3.domain.TrustAnchor;
import net.ripe.rpki.validator3.domain.TrustAnchorValidationRun;
import net.ripe.rpki.validator3.domain.ValidationCheck;
import net.ripe.rpki.validator3.domain.ValidationRun;
import net.ripe.rpki.validator3.domain.ValidationRuns;
import net.ripe.rpki.validator3.domain.querydsl.QCertificateTreeValidationRun;
import net.ripe.rpki.validator3.domain.querydsl.QRrdpRepositoryValidationRun;
import net.ripe.rpki.validator3.domain.querydsl.QRsyncRepositoryValidationRun;
import net.ripe.rpki.validator3.domain.querydsl.QTrustAnchorValidationRun;
import net.ripe.rpki.validator3.domain.querydsl.QValidationRun;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import javax.transaction.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static net.ripe.rpki.validator3.domain.querydsl.QCertificateTreeValidationRun.certificateTreeValidationRun;
import static net.ripe.rpki.validator3.domain.querydsl.QRrdpRepositoryValidationRun.rrdpRepositoryValidationRun;
import static net.ripe.rpki.validator3.domain.querydsl.QRsyncRepositoryValidationRun.rsyncRepositoryValidationRun;
import static net.ripe.rpki.validator3.domain.querydsl.QTrustAnchorValidationRun.trustAnchorValidationRun;
import static net.ripe.rpki.validator3.domain.querydsl.QValidationCheck.validationCheck;
import static net.ripe.rpki.validator3.domain.querydsl.QValidationRun.validationRun;

@Repository
@Transactional(Transactional.TxType.REQUIRED)
public class JPAValidationRuns extends JPARepository<ValidationRun> implements ValidationRuns {
    private final QuartzValidationScheduler validationScheduler;

    @Autowired
    public JPAValidationRuns(QuartzValidationScheduler validationScheduler) {
        super(validationRun);
        this.validationScheduler = validationScheduler;
    }

    protected static <T extends ValidationRun> JPQLQuery<Long> latestSuccessfulValidationRuns() {
        QValidationRun latest = new QValidationRun("latest");
        return JPAExpressions
            .select(latest.id.max())
            .where(latest.status.eq(ValidationRun.Status.SUCCEEDED))
            .groupBy(
                JPAExpressions.type(latest),
                latest.as(QTrustAnchorValidationRun.class).trustAnchor,
                latest.as(QCertificateTreeValidationRun.class).trustAnchor,
                latest.as(QRrdpRepositoryValidationRun.class).rpkiRepository
            )
            .from(latest);
    }

    @Override
    public void removeAllForTrustAnchor(TrustAnchor trustAnchor) {
        queryFactory.delete(trustAnchorValidationRun).where(trustAnchorValidationRun.trustAnchor.eq(trustAnchor)).execute();
        queryFactory.delete(certificateTreeValidationRun).where(certificateTreeValidationRun.trustAnchor.eq(trustAnchor)).execute();
    }

    @Override
    public <T extends ValidationRun> List<T> findAll(Class<T> type) {
        return select(type)
            .orderBy(validationRun.updatedAt.desc(), validationRun.id.desc())
            .fetch();
    }

    @Override
    public <T extends ValidationRun> List<T> findLatestSuccessful(Class<T> type) {
        return select(type)
            .where(validationRun.id.in(latestSuccessfulValidationRuns()))
            .orderBy(validationRun.updatedAt.desc(), validationRun.id.desc())
            .fetch();
    }

    @Override
    public Optional<TrustAnchorValidationRun> findLatestCompletedForTrustAnchor(TrustAnchor trustAnchor) {
        return Optional.ofNullable(
            select(TrustAnchorValidationRun.class)
                .where(
                    validationRun.as(QTrustAnchorValidationRun.class).trustAnchor.eq(trustAnchor)
                        .and(validationRun.status.in(ValidationRun.Status.FAILED, ValidationRun.Status.SUCCEEDED))
                )
                .orderBy(validationRun.updatedAt.desc(), validationRun.id.desc())
                .fetchFirst()
        );
    }

    @Override
    public void runCertificateTreeValidation(TrustAnchor trustAnchor) {
        validationScheduler.triggerCertificateTreeValidation(trustAnchor);
    }

    @Override
    public void removeAllForRpkiRepository(RpkiRepository repository) {
        if (repository.getType() == RpkiRepository.Type.RRDP) {
            queryFactory.delete(rrdpRepositoryValidationRun).where(rrdpRepositoryValidationRun.rpkiRepository.eq(repository)).execute();
        } else {
            // RPKI repository deletion for rsync validation runs cascades on delete in the DB, nothing to do here
        }
    }

    @Override
    public long removeOldValidationRuns(Instant completedBefore) {
        long removedCount = 0;
        removedCount += removeOldRsyncRepositoryValidationRuns(completedBefore);
        removedCount += removeOldRrdpRepositoryValidationRuns(completedBefore);
        removedCount += removeOldCertificateTreeValidationRuns(completedBefore);
        removedCount += removeOldTrustAnchorValidationRuns(completedBefore);
        return removedCount;
    }

    @Override
    public Stream<ValidationCheck> findValidationChecksForValidationRun(long validationRunId, Paging paging, SearchTerm searchTerm, Sorting sorting) {
        return stream(
            validationChecksQuery(validationRunId, searchTerm)
                .orderBy(toOrderSpecifier(sorting))
                .offset(paging.getStartFrom())
                .limit(paging.getPageSize())
        );
    }

    @Override
    public int countValidationChecksForValidationRun(long validationRunId, SearchTerm searchTerm) {
        return (int) validationChecksQuery(validationRunId, searchTerm).fetchCount();
    }

    private JPAQuery<ValidationCheck> validationChecksQuery(long validationRunId, SearchTerm searchTerm) {
        QValidationRun latest = new QValidationRun("latest");
        JPQLQuery<Long> validationRunIds = JPAExpressions
            .select(latest.id.max())
            .where(latest.status.eq(ValidationRun.Status.SUCCEEDED).and(latest.as(QCertificateTreeValidationRun.class).trustAnchor.id.eq(validationRunId)))
            .groupBy(
                JPAExpressions.type(latest),
                latest.as(QTrustAnchorValidationRun.class).trustAnchor,
                latest.as(QCertificateTreeValidationRun.class).trustAnchor
            )
            .from(latest);
        return queryFactory
            .selectFrom(validationCheck)
            .where(validationCheck.validationRun.id.in(validationRunIds).and(toPredicate(searchTerm)));
    }

    private Predicate toPredicate(SearchTerm searchTerm) {
        if (searchTerm == null) {
            return null;
        } else {
            return ExpressionUtils.anyOf(
                validationCheck.key.likeIgnoreCase("%" + searchTerm.asString() + "%"),
                validationCheck.status.stringValue().likeIgnoreCase("%" + searchTerm.asString() + "%"),
                validationCheck.parameters.any().likeIgnoreCase("%" + searchTerm.asString() + "%")
            );
        }
    }

    private OrderSpecifier<?> toOrderSpecifier(Sorting sorting) {
        ComparableExpression<?> column;
        switch (sorting.getBy()) {
            case KEY:
                column = validationCheck.key;
                break;
            case STATUS:
                column = validationCheck.status;
                break;
            case LOCATION:
                column = validationCheck.location;
                break;
            default:
                column = validationCheck.createdAt;
                break;
        }
        return sorting.getDirection() == Sorting.Direction.ASC ? column.asc() : column.desc();
    }

    private long removeOldRsyncRepositoryValidationRuns(Instant completedBefore) {
        QRsyncRepositoryValidationRun newerValidationRun = new QRsyncRepositoryValidationRun("newer");
        return queryFactory.delete(rsyncRepositoryValidationRun).where(
            rsyncRepositoryValidationRun.completedAt.before(completedBefore)
                .and(rsyncRepositoryValidationRun.status.ne(ValidationRun.Status.RUNNING))
                .and(JPAExpressions.selectFrom(newerValidationRun)
                    .where(newerValidationRun.id.gt(rsyncRepositoryValidationRun.id)
                        .and(newerValidationRun.status.ne(ValidationRun.Status.RUNNING))
                    ).exists()
                )
        ).execute();
    }

    private long removeOldRrdpRepositoryValidationRuns(Instant completedBefore) {
        QRrdpRepositoryValidationRun newerValidationRun = new QRrdpRepositoryValidationRun("newer");
        return queryFactory.delete(rrdpRepositoryValidationRun).where(
            rrdpRepositoryValidationRun.completedAt.before(completedBefore)
                .and(rrdpRepositoryValidationRun.status.ne(ValidationRun.Status.RUNNING))
                .and(JPAExpressions.selectFrom(newerValidationRun)
                    .where(newerValidationRun.id.gt(rrdpRepositoryValidationRun.id)
                        .and(rrdpRepositoryValidationRun.rpkiRepository.eq(newerValidationRun.rpkiRepository))
                        .and(newerValidationRun.status.ne(ValidationRun.Status.RUNNING))
                    ).exists()
                )
        ).execute();
    }

    private long removeOldCertificateTreeValidationRuns(Instant completedBefore) {
        QCertificateTreeValidationRun newerValidationRun = new QCertificateTreeValidationRun("newer");
        return queryFactory.delete(certificateTreeValidationRun).where(
            certificateTreeValidationRun.completedAt.before(completedBefore)
                .and(certificateTreeValidationRun.status.ne(ValidationRun.Status.RUNNING))
                .and(JPAExpressions.selectFrom(newerValidationRun)
                    .where(newerValidationRun.id.gt(certificateTreeValidationRun.id)
                        .and(certificateTreeValidationRun.trustAnchor.eq(newerValidationRun.trustAnchor))
                        .and(newerValidationRun.status.ne(ValidationRun.Status.RUNNING))
                    ).exists()
                )
        ).execute();
    }

    private long removeOldTrustAnchorValidationRuns(Instant completedBefore) {
        QTrustAnchorValidationRun newerValidationRun = new QTrustAnchorValidationRun("newer");
        return queryFactory.delete(trustAnchorValidationRun).where(
            trustAnchorValidationRun.completedAt.before(completedBefore)
                .and(trustAnchorValidationRun.status.ne(ValidationRun.Status.RUNNING))
                .and(JPAExpressions.selectFrom(newerValidationRun)
                    .where(newerValidationRun.id.gt(trustAnchorValidationRun.id)
                        .and(trustAnchorValidationRun.trustAnchor.eq(newerValidationRun.trustAnchor))
                        .and(newerValidationRun.status.ne(ValidationRun.Status.RUNNING))
                    ).exists()
                )
        ).execute();
    }

    protected <T extends ValidationRun> JPAQuery<T> select(Class<T> type) {
        return queryFactory.selectFrom(new PathBuilder<>(type, "validationRun"));
    }
}
