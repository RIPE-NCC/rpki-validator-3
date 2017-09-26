package net.ripe.rpki.validator3.adapter.jpa;

import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import net.ripe.rpki.validator3.domain.TrustAnchor;
import net.ripe.rpki.validator3.domain.TrustAnchorRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import javax.persistence.EntityManager;
import javax.persistence.EntityNotFoundException;
import javax.transaction.Transactional;
import javax.validation.Valid;
import java.util.List;

import static net.ripe.rpki.validator3.domain.querydsl.QTrustAnchor.trustAnchor;

@Component
@Transactional(Transactional.TxType.REQUIRED)
@Validated
public class JPATrustAnchorRepository implements TrustAnchorRepository {

    private final EntityManager entityManager;

    private final JPAQueryFactory queryFactory;

    @Autowired
    public JPATrustAnchorRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
        this.queryFactory = new JPAQueryFactory(entityManager);
    }

    @Override
    public void add(@Valid TrustAnchor trustAnchor) {
        entityManager.persist(trustAnchor);
    }

    @Override
    public TrustAnchor get(long id) throws EntityNotFoundException {
        TrustAnchor result = entityManager.getReference(TrustAnchor.class, id);
        result.getId();
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
