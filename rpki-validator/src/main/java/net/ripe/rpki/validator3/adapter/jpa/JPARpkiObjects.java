/**
 * The BSD License
 *
 * Copyright (c) 2010-2018 RIPE NCC
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *   - Redistributions of source code must retain the above copyright notice,
 *     this list of conditions and the following disclaimer.
 *   - Redistributions in binary form must reproduce the above copyright notice,
 *     this list of conditions and the following disclaimer in the documentation
 *     and/or other materials provided with the distribution.
 *   - Neither the name of the RIPE NCC nor the names of its contributors may be
 *     used to endorse or promote products derived from this software without
 *     specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package net.ripe.rpki.validator3.adapter.jpa;

import com.google.common.primitives.UnsignedBytes;
import com.querydsl.core.Tuple;
import com.querydsl.jpa.impl.JPAQuery;
import lombok.Value;
import net.ripe.rpki.commons.crypto.cms.manifest.ManifestCms;
import net.ripe.rpki.validator3.domain.CertificateTreeValidationRun;
import net.ripe.rpki.validator3.domain.RpkiObject;
import net.ripe.rpki.validator3.domain.RpkiObjects;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.stereotype.Repository;

import javax.persistence.LockModeType;
import javax.transaction.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Collectors;
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
    public Map<String, RpkiObject> findObjectsInManifest(ManifestCms manifestCms, LockModeType lockMode) {
        SortedMap<byte[], String> hashes = new TreeMap<>(UnsignedBytes.lexicographicalComparator());
        manifestCms.getFiles().forEach((name, hash) -> hashes.put(hash, name));

        List<RpkiObject> objects = select().setLockMode(lockMode).where(rpkiObject.sha256.in(hashes.keySet())).fetch();

        return objects.stream().collect(Collectors.toMap(object -> hashes.get(object.getSha256()), object -> object));
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
    @SuppressWarnings("unchecked")
    public Stream<RoaPrefix> findCurrentlyValidatedRoaPrefixes(Integer startFrom, Integer pageSize) {
        String sql = "SELECT DISTINCT \n" +
                "      p.asn AS asn, \n" +
                "      p.prefix AS prefix, \n" +
                "      COALESCE(p.effective_length, p.maximum_length) AS length, \n" +
                "      ta.name AS trust_anchor, \n" +
                "      (SELECT locations \n" +
                "       FROM rpki_object_locations \n" +
                "       WHERE rpki_object_id = ro.id \n" +
                "       LIMIT 1 \n" +
                "  ) AS location  \n" +
                "  FROM rpki_object ro \n" +
                "  INNER JOIN rpki_object_roa_prefixes p ON p.rpki_object_id = ro.id \n" +
                "  INNER JOIN validation_run_validated_objects vrvo ON vrvo.rpki_object_id = ro.id \n" +
                "  INNER JOIN validation_run vr ON vr.id = vrvo.validation_run_id \n" +
                "  INNER JOIN trust_anchor ta ON vr.trust_anchor_id = ta.id \n" +
                "  WHERE vr.type = 'CT' \n" +
                "  AND vr.id in (\n" +
                "    SELECT MAX(id)\n" +
                "    FROM validation_run vr1\n" +
                "    WHERE vr1.type = vr.type \n" +
                "    GROUP BY vr1.trust_anchor_id, vr1.rpki_repository_id \n" +
                "  )" +
                "  ORDER BY ta.name, p.asn, p.prefix ";

        int sf = startFrom == null ? 0 : startFrom;
        if (pageSize != null) {
            sql = sql + " LIMIT " + pageSize;
        }
        if (startFrom != null) {
            sql = sql + " OFFSET " + startFrom;
        }

        return sql(sql).getResultList().stream().map(o -> {
            final Object[] fields = (Object[]) o;
            return new RoaPrefix(
                    asString(fields[0]),
                    asString(fields[1]),
                    asInt(fields[2]),
                    asString(fields[3]),
                    asString(fields[4]));
        });
    }

    private String asString(Object o) {
        return o == null ? null : o.toString();
    }

    private int asInt(Object o) {
        return o == null ? null : Integer.parseInt(o.toString());
    }


    @Value
    public static class RoaPrefix {
        private String asn;
        private String prefix;
        private int length;
        private String trustAnchor;
        private String uri;
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
                ).select(certificateTreeValidationRun, rpkiObject);
        return stream(query).map(x -> Pair.of(x.get(0, CertificateTreeValidationRun.class), x.get(1, RpkiObject.class)));
    }

    @Override
    public Stream<RpkiObject> findRouterCertificates() {
        return stream(select().where(rpkiObject.type.eq(RpkiObject.Type.ROUTER_CER)));
    }

    @Override
    public long deleteUnreachableObjects(Instant unreachableSince) {
        return queryFactory.delete(rpkiObject).where(rpkiObject.lastMarkedReachableAt.before(unreachableSince)).execute();
    }

    @Override
    public void merge(RpkiObject object) {
        entityManager.merge(object);
    }
}
