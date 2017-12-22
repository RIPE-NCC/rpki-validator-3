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
package net.ripe.rpki.rtr.adapter.jpa;

import net.ripe.rpki.rtr.domain.TrustAnchor;
import net.ripe.rpki.rtr.domain.TrustAnchors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import javax.transaction.Transactional;
import java.util.List;
import java.util.Optional;

import static net.ripe.rpki.rtr.domain.querydsl.QTrustAnchor.trustAnchor;

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

    @Override
    public Optional<TrustAnchor> findBySubjectPublicKeyInfo(String subjectPublicKeyInfo) {
        return Optional.ofNullable(select().where(trustAnchor.subjectPublicKeyInfo.eq(subjectPublicKeyInfo)).fetchOne());
    }
}
