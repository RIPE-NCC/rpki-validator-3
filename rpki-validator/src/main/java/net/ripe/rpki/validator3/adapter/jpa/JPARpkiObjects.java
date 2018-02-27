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
    public long deleteUnreachableObjects(Instant unreachableSince) {
        return queryFactory.delete(rpkiObject).where(rpkiObject.lastMarkedReachableAt.before(unreachableSince)).execute();
    }

    @Override
    public void merge(RpkiObject object) {
        entityManager.merge(object);
    }
}
