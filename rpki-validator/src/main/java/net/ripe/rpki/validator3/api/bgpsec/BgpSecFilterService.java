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
package net.ripe.rpki.validator3.api.bgpsec;

import lombok.extern.slf4j.Slf4j;
import net.ripe.ipresource.Asn;
import net.ripe.rpki.validator3.api.slurm.SlurmStore;
import net.ripe.rpki.validator3.api.slurm.dtos.Slurm;
import net.ripe.rpki.validator3.domain.validation.ValidatedRpkiObjects;
import javax.inject.Inject;
import javax.inject.Singleton;
import io.micronaut.validation.Validated;

import javax.validation.Valid;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Singleton
@Validated
@Slf4j
public class BgpSecFilterService {

    private final SlurmStore slurmStore;

    @Inject
    public BgpSecFilterService(SlurmStore slurmStore) {
        this.slurmStore = slurmStore;
    }

    public long execute(@Valid AddBgpSecFilter command) {
        final long id = slurmStore.nextId();
        return slurmStore.updateWith(slurmExt -> {
            final Slurm.SlurmBgpSecFilter slurmBgpSecFilter = new Slurm.SlurmBgpSecFilter();
            slurmBgpSecFilter.setAsn(Asn.parse(command.getAsn()));
            slurmBgpSecFilter.setSki(command.getSki());
            slurmBgpSecFilter.setComment(command.getComment());
            slurmExt.getBgpsecFilters().put(id, slurmBgpSecFilter);
            return id;
        });
    }

    public void remove(long id) {
        slurmStore.updateWith(slurmExt -> {
            slurmExt.getBgpsecFilters().remove(id);
        });
    }

    public Stream<BgpSecFilter> all() {
        return slurmStore.read().getBgpsecFilters()
                .entrySet().stream()
                .map(e -> {
                    Slurm.SlurmBgpSecFilter v = e.getValue();
                    return new BgpSecFilter(e.getKey(), v.getAsn(), v.getSki(), v.getComment());
                });
    }

    public void clear() {
        slurmStore.updateWith(slurmExt -> {
            slurmExt.getBgpsecFilters().clear();
        });
    }

    public Stream<ValidatedRpkiObjects.RouterCertificate> filterCertificates(final Stream<ValidatedRpkiObjects.RouterCertificate> routerCertificates) {
        return filterCertificates(routerCertificates, all().collect(Collectors.toList()));
    }

    Stream<ValidatedRpkiObjects.RouterCertificate> filterCertificates(
            final Stream<ValidatedRpkiObjects.RouterCertificate> routerCertificates,
            final List<BgpSecFilter> filters) {
        return routerCertificates.filter(rc -> {
            final List<Long> longAsns = rc.getAsn() != null ?
                    rc.getAsn().stream().map(asn -> Asn.parse(asn).longValue()).collect(Collectors.toList()) :
                    Collections.emptyList();

            return filters.stream().noneMatch(f -> {
                boolean keepIt = true;
                final Asn asn = f.getAsn();
                if (asn != null) {
                    keepIt = longAsns.stream().anyMatch(a -> a == asn.longValue());
                }
                if (f.getSki() != null) {
                    keepIt = keepIt && f.getSki().equals(rc.getSubjectKeyIdentifier());
                }
                return keepIt;
            });
        });
    }
}
