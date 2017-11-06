package net.ripe.rpki.validator3.adapter.jpa;

import net.ripe.rpki.validator3.domain.RpkiRepositories;
import net.ripe.rpki.validator3.domain.RpkiRepository;
import net.ripe.rpki.validator3.domain.TrustAnchor;
import net.ripe.rpki.validator3.domain.constraints.ValidLocationURI;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import javax.transaction.Transactional;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.List;
import java.util.Optional;

import static net.ripe.rpki.validator3.domain.querydsl.QRpkiRepository.rpkiRepository;

@Repository
@Transactional(Transactional.TxType.REQUIRED)
public class JPARpkiRepositories extends JPARepository<RpkiRepository> implements RpkiRepositories {
    private final QuartzValidationScheduler quartzValidationScheduler;

    @Autowired
    public JPARpkiRepositories(QuartzValidationScheduler quartzValidationScheduler) {
        super(rpkiRepository);
        this.quartzValidationScheduler = quartzValidationScheduler;
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
    public List<RpkiRepository> findAll() {
        return select().fetch();
    }
}
