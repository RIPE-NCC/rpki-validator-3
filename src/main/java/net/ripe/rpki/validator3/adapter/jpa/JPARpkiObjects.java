package net.ripe.rpki.validator3.adapter.jpa;

import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import net.ripe.rpki.validator3.domain.RpkiObject;
import net.ripe.rpki.validator3.domain.RpkiObjects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import java.util.List;
import java.util.Optional;

import static net.ripe.rpki.validator3.domain.querydsl.QCertificateTreeValidationRun.certificateTreeValidationRun;
import static net.ripe.rpki.validator3.domain.querydsl.QRpkiObject.rpkiObject;

@Repository
@Transactional(Transactional.TxType.REQUIRED)
public class JPARpkiObjects implements RpkiObjects {

    private final EntityManager entityManager;

    private final JPAQueryFactory queryFactory;

    @Autowired
    public JPARpkiObjects(EntityManager entityManager, JPAQueryFactory queryFactory) {
        this.entityManager = entityManager;
        this.queryFactory = queryFactory;
    }

    @Override
    public void add(RpkiObject trustAnchor) {
        entityManager.persist(trustAnchor);
    }

    @Override
    public void remove(RpkiObject o) {
        entityManager.remove(o);
    }

    @Override
    public RpkiObject get(long id) {
        RpkiObject result = entityManager.getReference(RpkiObject.class, id);
        result.getId(); // Throws EntityNotFoundException if the id is not valid
        return result;
    }

    @Override
    public Optional<RpkiObject> findBySha256(byte[] sha256) {
        return Optional.ofNullable(select().where(rpkiObject.sha256.eq(sha256)).fetchFirst());
    }

    @Override
    public List<RpkiObject> all() {
        return select().fetch();
    }

    @Override
    public Optional<RpkiObject> findLatestByTypeAndAuthorityKeyIdentifier(RpkiObject.Type type, byte[] authorityKeyIdentifier) {
        return Optional.ofNullable(select()
            .where(rpkiObject.type.eq(type).and(rpkiObject.authorityKeyIdentifier.eq(authorityKeyIdentifier)))
            .orderBy(rpkiObject.serialNumber.desc(), rpkiObject.signingTime.desc(), rpkiObject.id.desc())
            .fetchFirst()
        );
    }

    @Override
    public List<RpkiObject> findCurrentlyValidated(RpkiObject.Type type) {
        return queryFactory
            .from(certificateTreeValidationRun)
            .join(certificateTreeValidationRun.validatedObjects, rpkiObject)
            .where(
                rpkiObject.type.eq(type)
                    .and(certificateTreeValidationRun.id.in(
                        JPAValidationRuns.latestSuccessfulValidationRuns())
                    )
            )
            .select(rpkiObject)
            .fetch();
    }

    private JPAQuery<RpkiObject> select() {
        return queryFactory.selectFrom(rpkiObject);
    }
}
