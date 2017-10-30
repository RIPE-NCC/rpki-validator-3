package net.ripe.rpki.validator3.adapter.jpa;

import com.querydsl.core.types.dsl.PathBuilder;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.JPQLQuery;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import net.ripe.rpki.validator3.domain.TrustAnchor;
import net.ripe.rpki.validator3.domain.TrustAnchorValidationRun;
import net.ripe.rpki.validator3.domain.ValidationRun;
import net.ripe.rpki.validator3.domain.ValidationRuns;
import net.ripe.rpki.validator3.domain.querydsl.QCertificateTreeValidationRun;
import net.ripe.rpki.validator3.domain.querydsl.QRpkiRepositoryValidationRun;
import net.ripe.rpki.validator3.domain.querydsl.QTrustAnchorValidationRun;
import net.ripe.rpki.validator3.domain.querydsl.QValidationRun;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import java.util.List;
import java.util.Optional;

import static net.ripe.rpki.validator3.domain.querydsl.QTrustAnchorValidationRun.trustAnchorValidationRun;
import static net.ripe.rpki.validator3.domain.querydsl.QValidationRun.validationRun;

@Repository
@Transactional(Transactional.TxType.REQUIRED)
public class JPAValidationRuns implements ValidationRuns {
    private final EntityManager entityManager;
    private final JPAQueryFactory jpaQueryFactory;
    private final QuartzValidationScheduler validationScheduler;

    @Autowired
    public JPAValidationRuns(EntityManager entityManager, JPAQueryFactory jpaQueryFactory, QuartzValidationScheduler validationScheduler) {
        this.entityManager = entityManager;
        this.jpaQueryFactory = jpaQueryFactory;
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
                latest.as(QRpkiRepositoryValidationRun.class).rpkiRepository
            )
            .from(latest);
    }

    @Override
    public void add(ValidationRun validationRun) {
        entityManager.persist(validationRun);
    }

    @Override
    public void removeAllForTrustAnchor(TrustAnchor trustAnchor) {
        jpaQueryFactory.delete(trustAnchorValidationRun).where(trustAnchorValidationRun.trustAnchor.id.eq(trustAnchor.getId())).execute();
    }

    @Override
    public <T extends ValidationRun> T get(Class<T> type, long id) {
        T result = entityManager.getReference(type, id);
        result.getId(); // Throws EntityNotFoundException if the id is not valid
        return result;
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

    private <T extends ValidationRun> JPAQuery<T> select(Class<T> type) {
        return jpaQueryFactory.selectFrom(new PathBuilder<>(type, "validationRun"));
    }
}
