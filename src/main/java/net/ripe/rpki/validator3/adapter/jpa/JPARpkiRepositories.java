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
import java.util.List;
import java.util.Optional;

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

    @Override
    public RpkiRepository register(@NotNull @Valid TrustAnchor trustAnchor, @NotNull @ValidLocationURI String uri) {
        RpkiRepository result = findByURI(uri).orElseGet(() -> {
            RpkiRepository repository = new RpkiRepository(trustAnchor, uri.toLowerCase());
            entityManager.persist(repository);
            quartzValidationScheduler.addRpkiRepository(repository);
            return repository;
        });
        result.addTrustAnchor(trustAnchor);
        return result;
    }

    @Override
    public Optional<RpkiRepository> findByURI(@NotNull @ValidLocationURI String uri) {
        return Optional.ofNullable(select().where(rpkiRepository.rrdpNotifyUri.eq(uri.toLowerCase())).fetchFirst());
    }

    @Override
    public RpkiRepository get(long id) {
        RpkiRepository result = entityManager.getReference(RpkiRepository.class, id);
        result.getId(); // Throws EntityNotFoundException if the id is not valid
        return result;
    }

    @Override
    public List<RpkiRepository> findAll() {
        return select().fetch();
    }

    private JPAQuery<RpkiRepository> select() {
        return jpaQueryFactory.selectFrom(rpkiRepository);
    }
}
