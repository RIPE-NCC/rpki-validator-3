package net.ripe.rpki.validator3.adapter.jpa;

import net.ripe.rpki.validator3.domain.TrustAnchor;
import net.ripe.rpki.validator3.domain.TrustAnchors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import javax.transaction.Transactional;
import java.util.List;

import static net.ripe.rpki.validator3.domain.querydsl.QTrustAnchor.trustAnchor;

@Repository
@Transactional(Transactional.TxType.REQUIRED)
public class JPATrustAnchors extends JPARepository<TrustAnchor> implements TrustAnchors {

    private final QuartzValidationScheduler validationScheduler;

    @Autowired
    public JPATrustAnchors(QuartzValidationScheduler validationScheduler) {
        super(trustAnchor);
        this.validationScheduler = validationScheduler;
    }

    @Override
    public void add(TrustAnchor trustAnchor) {
        super.add(trustAnchor);
        validationScheduler.addTrustAnchor(trustAnchor);
    }

    @Override
    public void remove(TrustAnchor trustAnchor) {
        validationScheduler.removeTrustAnchor(trustAnchor);
        super.remove(trustAnchor);
    }

    @Override
    public List<TrustAnchor> findAll() {
        return select().orderBy(trustAnchor.id.asc()).fetch();
    }

    @Override
    public List<TrustAnchor> findByName(String name) {
        return select().where(trustAnchor.name.eq(name)).orderBy(trustAnchor.name.asc(), trustAnchor.id.asc()).fetch();
    }
}
