package net.ripe.rpki.validator3.adapter.jpa;

import com.querydsl.jpa.impl.JPAQueryFactory;
import net.ripe.rpki.validator3.domain.TrustAnchor;
import net.ripe.rpki.validator3.domain.ValidationRun;
import net.ripe.rpki.validator3.domain.ValidationRunRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import javax.persistence.EntityManager;
import javax.transaction.Transactional;

import static net.ripe.rpki.validator3.domain.querydsl.QValidationRun.validationRun;

@Repository
@Transactional(Transactional.TxType.REQUIRED)
public class JPAValidationRunRepository implements ValidationRunRepository {
    private final EntityManager entityManager;
    private final JPAQueryFactory jpaQueryFactory;

    @Autowired
    public JPAValidationRunRepository(EntityManager entityManager, JPAQueryFactory jpaQueryFactory) {
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
}
