package net.ripe.rpki.validator3.adapter.jpa;

import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import net.ripe.rpki.validator3.domain.RpkiRepositories;
import net.ripe.rpki.validator3.domain.RpkiRepository;
import net.ripe.rpki.validator3.domain.TrustAnchor;
import net.ripe.rpki.validator3.domain.constraints.ValidLocationURI;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;

import static net.ripe.rpki.validator3.domain.querydsl.QRpkiRepository.rpkiRepository;

@Repository
@Transactional(Transactional.TxType.REQUIRED)
public class JPARpkiRepositories implements RpkiRepositories {
    private final EntityManager entityManager;
    private final QuartzValidationScheduler quartzValidationScheduler;
    private final JPAQueryFactory jpaQueryFactory;

    @Autowired
    public JPARpkiRepositories(EntityManager entityManager, QuartzValidationScheduler quartzValidationScheduler, JPAQueryFactory jpaQueryFactory) {
        this.entityManager = entityManager;
        this.quartzValidationScheduler = quartzValidationScheduler;
        this.jpaQueryFactory = jpaQueryFactory;
    }



    private JPAQuery<RpkiRepository> select() {
        return jpaQueryFactory.selectFrom(rpkiRepository);
    }

    @Override
    public void register(@NotNull @Valid TrustAnchor trustAnchor, @NotNull @ValidLocationURI String uri) {
        RpkiRepository existing = select().where(rpkiRepository.trustAnchor.eq(trustAnchor).and(rpkiRepository.uri.eq(uri.toLowerCase()))).fetchFirst();
        if (existing == null) {
            RpkiRepository repository = new RpkiRepository(trustAnchor, uri.toLowerCase());
            entityManager.persist(repository);
            quartzValidationScheduler.addRpkiRepository(repository);
        }
    }

    @Override
    public RpkiRepository get(long id) {
        RpkiRepository result = entityManager.getReference(RpkiRepository.class, id);
        result.getId(); // Throws EntityNotFoundException if the id is not valid
        return result;
    }
}
