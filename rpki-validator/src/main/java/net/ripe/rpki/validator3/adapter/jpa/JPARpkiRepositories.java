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

import com.querydsl.core.BooleanBuilder;
import net.ripe.rpki.validator3.domain.RpkiRepositories;
import net.ripe.rpki.validator3.domain.RpkiRepository;
import net.ripe.rpki.validator3.domain.TrustAnchor;
import net.ripe.rpki.validator3.domain.ValidationRuns;
import net.ripe.rpki.validator3.domain.constraints.ValidLocationURI;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import javax.transaction.Transactional;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static net.ripe.rpki.validator3.domain.querydsl.QRpkiRepository.rpkiRepository;

@Repository
@Transactional(Transactional.TxType.REQUIRED)
public class JPARpkiRepositories extends JPARepository<RpkiRepository> implements RpkiRepositories {
    private final QuartzValidationScheduler quartzValidationScheduler;
    private final ValidationRuns validationRuns;

    @Autowired
    public JPARpkiRepositories(QuartzValidationScheduler quartzValidationScheduler, ValidationRuns validationRuns) {
        super(rpkiRepository);
        this.quartzValidationScheduler = quartzValidationScheduler;
        this.validationRuns = validationRuns;
    }

    @Override
    public RpkiRepository register(@NotNull @Valid TrustAnchor trustAnchor, @NotNull @ValidLocationURI String uri, RpkiRepository.Type type) {
        RpkiRepository result = findByURI(uri).orElseGet(() -> {
            RpkiRepository repository = new RpkiRepository(trustAnchor, uri, type);
            entityManager.persist(repository);
            if (repository.getType() == RpkiRepository.Type.RRDP) {
                quartzValidationScheduler.addRpkiRepository(repository);
            }
            return repository;
        });
        result.addTrustAnchor(trustAnchor);
        if (type == RpkiRepository.Type.RSYNC && result.getType() == RpkiRepository.Type.RSYNC_PREFETCH) {
            result.setType(RpkiRepository.Type.RSYNC);
        }
        return result;
    }

    @Override
    public Optional<RpkiRepository> findByURI(@NotNull @ValidLocationURI String uri) {
        String normalized = uri;
        return Optional.ofNullable(select().where(
            rpkiRepository.rrdpNotifyUri.eq(normalized).or(rpkiRepository.rsyncRepositoryUri.eq(normalized))
        ).fetchFirst());
    }

    @Override
    public List<RpkiRepository> findAll(RpkiRepository.Status optionalStatus) {
        BooleanBuilder builder = new BooleanBuilder();
        if (optionalStatus != null) {
            builder.and(rpkiRepository.status.eq(optionalStatus));
        }
        return select().where(builder).fetch();
    }

    @Override
    public Stream<RpkiRepository> findRsyncRepositories() {
        return stream(
            select()
                .where(rpkiRepository.type.in(RpkiRepository.Type.RSYNC, RpkiRepository.Type.RSYNC_PREFETCH))
                .orderBy(rpkiRepository.rsyncRepositoryUri.asc(), rpkiRepository.id.asc())
        );
    }

    @Override
    public void removeAllForTrustAnchor(TrustAnchor trustAnchor) {
        for (RpkiRepository repository : select().where(rpkiRepository.trustAnchors.contains(trustAnchor)).fetch()) {
            repository.removeTrustAnchor(trustAnchor);
            if (repository.getTrustAnchors().isEmpty()) {
                if (repository.getType() == RpkiRepository.Type.RRDP) {
                    quartzValidationScheduler.removeRpkiRepository(repository);
                }
                validationRuns.removeAllForRpkiRepository(repository);
                entityManager.remove(repository);
            }
        }
    }
}
