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

import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.validator3.api.trustanchors.TaStatus;
import net.ripe.rpki.validator3.api.util.Dates;
import net.ripe.rpki.validator3.domain.TrustAnchor;
import net.ripe.rpki.validator3.domain.TrustAnchors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import javax.transaction.Transactional;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static net.ripe.rpki.validator3.domain.querydsl.QTrustAnchor.trustAnchor;

@Repository
@Transactional(Transactional.TxType.REQUIRED)
@Slf4j
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

    @Override
    public Optional<TrustAnchor> findBySubjectPublicKeyInfo(String subjectPublicKeyInfo) {
        return Optional.ofNullable(select().where(trustAnchor.subjectPublicKeyInfo.eq(subjectPublicKeyInfo)).fetchOne());
    }

    @Override
    public boolean allInitialCertificateTreeValidationRunsCompleted() {
        long l = queryFactory.from(trustAnchor).select(trustAnchor.id).where(trustAnchor.initialCertificateTreeValidationRunCompleted.eq(false)).fetchCount();
        log.debug("still {} trust anchors need to complete initial validation run", l);
        return l == 0;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<TaStatus> getStatuses() {
        String sql = "SELECT " +
                "       taId, taName, \n" +
                "       SUM(errors), \n" +
                "       SUM(warnings), " +
                "       (SELECT COUNT(DISTINCT vrvo.rpki_object_id) \n" +
                "        FROM validation_run_validated_objects vrvo \n" +
                "        WHERE vrvo.validation_run_id = vrid        \n" +
                "       ) AS successful, \n" +
                "       (SELECT MAX(completed_at) \n" +
                "        FROM validation_run \n" +
                "        WHERE trust_anchor_id = taId \n" +
                "        AND type = 'CT' \n" +
                "       ) AS lastUpdated, \n" +
                "       completedValidation \n" +
                "     FROM (\n" +
                "     SELECT DISTINCT \n" +
                "       ta.id as taId, \n" +
                "       ta.name as taName, \n" +
                "        CASE WHEN vc.status = 'ERROR'   THEN 1 ELSE 0 END AS errors,\n" +
                "        CASE WHEN vc.status = 'WARNING' THEN 1 ELSE 0 END AS warnings,\n" +
                "        vr.id as vrid,\n" +
                "        vc.location, \n" +
                "        initial_certificate_tree_validation_run_completed AS completedValidation \n" +
                "     FROM trust_anchor ta\n" +
                "     INNER JOIN validation_run vr ON vr.trust_anchor_id = ta.id\n" +
                "     LEFT JOIN validation_check vc ON vc.validation_run_id = vr.id\n" +
                "     WHERE vr.id in (\n" +
                "       SELECT MAX(id)\n" +
                "       FROM validation_run vr1\n" +
                "       WHERE vr1.type = 'CT' \n" +
                "       GROUP BY vr1.trust_anchor_id, vr1.rpki_repository_id\n" +
                "     )\n" +
                "  )\n" +
                "GROUP BY taid";

        return ((Stream<TaStatus>) sql(sql).getResultList().stream().map(o -> {
            final Object[] fields = (Object[]) o;
            return TaStatus.of(
                    asString(fields[0]),
                    asString(fields[1]),
                    asInt(fields[2]),
                    asInt(fields[3]),
                    asInt(fields[4]),
                    Dates.formatUTC(fields[5]),
                    asBoolean(fields[6]));
        })).collect(Collectors.toList());
    }

}
