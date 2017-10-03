package net.ripe.rpki.validator3.adapter.jpa;

import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import net.ripe.rpki.validator3.domain.TrustAnchor;
import net.ripe.rpki.validator3.domain.TrustAnchorRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import java.util.List;

import static net.ripe.rpki.validator3.domain.querydsl.QTrustAnchor.trustAnchor;

@Repository
@Transactional(Transactional.TxType.REQUIRED)
public class JPATrustAnchorRepository implements TrustAnchorRepository {

    private final EntityManager entityManager;

    private final JPAQueryFactory queryFactory;

    private final QuartzValidationScheduler validationScheduler;

    @Autowired
    public JPATrustAnchorRepository(EntityManager entityManager, JPAQueryFactory queryFactory, QuartzValidationScheduler validationScheduler) {
        this.entityManager = entityManager;
        this.queryFactory = queryFactory;
        this.validationScheduler = validationScheduler;
    }

    @Override
    public void add(TrustAnchor trustAnchor) {
        entityManager.persist(trustAnchor);
        validationScheduler.addTrustAnchor(trustAnchor);
    }

    @Override
    public TrustAnchor get(long id) {
        TrustAnchor result = entityManager.getReference(TrustAnchor.class, id);
        result.getName(); // Throws EntityNotFoundException if the id is not valid
        return result;
    }

    @Override
    public List<TrustAnchor> findAll() {
        return select().orderBy(trustAnchor.id.asc()).fetch();
    }

    @Override
    public List<TrustAnchor> findByName(String name) {
        return select().where(trustAnchor.name.eq(name)).orderBy(trustAnchor.name.asc(), trustAnchor.id.asc()).fetch();
    }

    private JPAQuery<TrustAnchor> select() {
        return queryFactory.selectFrom(trustAnchor);
    }
}
