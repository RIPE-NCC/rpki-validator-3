package net.ripe.rpki.validator3.adapter.jpa;

import com.querydsl.jpa.impl.JPAQueryFactory;
import net.ripe.rpki.validator3.domain.TrustAnchor;
import net.ripe.rpki.validator3.domain.TrustAnchorRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.springframework.validation.annotation.Validated;

import javax.persistence.EntityManager;
import java.util.List;

import static net.ripe.rpki.validator3.domain.QTrustAnchor.trustAnchor;

@Repository
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
    public void add(TrustAnchor trustAnchor) {
        entityManager.persist(trustAnchor);
    }

    @Override
    public List<TrustAnchor> findByName(String name) {
        return queryFactory.selectFrom(trustAnchor).where(trustAnchor.name.eq(name)).fetch();
    }
}
