package net.ripe.rpki.validator3.adapter.jpa;

import com.querydsl.core.Tuple;
import com.querydsl.jpa.impl.JPAQuery;
import net.ripe.rpki.validator3.domain.CertificateTreeValidationRun;
import net.ripe.rpki.validator3.domain.RpkiObject;
import net.ripe.rpki.validator3.domain.RpkiObjects;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.stereotype.Repository;

import javax.transaction.Transactional;
import java.util.Optional;
import java.util.stream.Stream;

import static net.ripe.rpki.validator3.domain.querydsl.QCertificateTreeValidationRun.certificateTreeValidationRun;
import static net.ripe.rpki.validator3.domain.querydsl.QRpkiObject.rpkiObject;

@Repository
@Transactional(Transactional.TxType.REQUIRED)
public class JPARpkiObjects extends JPARepository<RpkiObject> implements RpkiObjects {

    public JPARpkiObjects() {
        super(rpkiObject);
    }

    @Override
    public Optional<RpkiObject> findBySha256(byte[] sha256) {
        return Optional.ofNullable(select().where(rpkiObject.sha256.eq(sha256)).fetchFirst());
    }

    @Override
    public Stream<RpkiObject> all() {
        return stream(select());
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
    public Stream<Pair<CertificateTreeValidationRun, RpkiObject>> findCurrentlyValidated(RpkiObject.Type type) {
        JPAQuery<Tuple> query = queryFactory
            .from(certificateTreeValidationRun)
            .join(certificateTreeValidationRun.validatedObjects, rpkiObject)
            .where(
                rpkiObject.type.eq(type)
                    .and(certificateTreeValidationRun.id.in(
                        JPAValidationRuns.latestSuccessfulValidationRuns())
                    )
            )
            .select(certificateTreeValidationRun, rpkiObject);
        return stream(query)
            .map(x -> Pair.of(x.get(0, CertificateTreeValidationRun.class), x.get(1, RpkiObject.class)));
    }

    @Override
    public Stream<Pair<CertificateTreeValidationRun, RpkiObject>> findCurrentlyValidated() {
        JPAQuery<Tuple> query = queryFactory
                .from(certificateTreeValidationRun)
                .join(certificateTreeValidationRun.validatedObjects, rpkiObject)
                .where(certificateTreeValidationRun.id.in(JPAValidationRuns.latestSuccessfulValidationRuns()))
                .select(certificateTreeValidationRun, rpkiObject);
        return stream(query)
                .map(x -> Pair.of(x.get(0, CertificateTreeValidationRun.class), x.get(1, RpkiObject.class)));
    }

    @Override
    public void merge(RpkiObject object) {
        entityManager.merge(object);
    }
}
