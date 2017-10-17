package net.ripe.rpki.validator3.adapter.jpa;

import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import net.ripe.rpki.validator3.domain.TrustAnchor;
import net.ripe.rpki.validator3.domain.ValidationRun;
import net.ripe.rpki.validator3.domain.ValidationRuns;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import java.util.List;
import java.util.Optional;

import static net.ripe.rpki.validator3.domain.querydsl.QValidationRun.validationRun;

@Repository
@Transactional(Transactional.TxType.REQUIRED)
public class JPAValidationRuns implements ValidationRuns {
    private final EntityManager entityManager;
    private final JPAQueryFactory jpaQueryFactory;

    @Autowired
    public JPAValidationRuns(EntityManager entityManager, JPAQueryFactory jpaQueryFactory) {
        this.entityManager = entityManager;
        this.jpaQueryFactory = jpaQueryFactory;
    }

    @Override
    public void add(ValidationRun validationRun) {
        entityManager.persist(validationRun);
    }

    @Override
    public void removeAllForTrustAnchor(TrustAnchor trustAnchor) {
        jpaQueryFactory.delete(validationRun).where(validationRun.trustAnchor.id.eq(trustAnchor.getId())).execute();
    }

    @Override
    public ValidationRun get(long id) {
        ValidationRun result = entityManager.getReference(ValidationRun.class, id);
        result.getId(); // Throws EntityNotFoundException if the id is not valid
        return result;
    }

    @Override
    public List<ValidationRun> findAll() {
        return select().orderBy(validationRun.updatedAt.desc(), validationRun.id.desc()).fetch();
    }

    @Override
    public Optional<ValidationRun> findLatestCompletedForTrustAnchor(TrustAnchor trustAnchor) {
        return Optional.ofNullable(
            select()
                .where(
                    validationRun.trustAnchor.eq(trustAnchor)
                        .and(validationRun.status.in(ValidationRun.Status.FAILED, ValidationRun.Status.SUCCEEDED))
                )
                .orderBy(validationRun.updatedAt.desc(), validationRun.id.desc())
                .fetchFirst()
        );
    }

    private JPAQuery<ValidationRun> select() {
        return jpaQueryFactory.selectFrom(validationRun);
    }
}
