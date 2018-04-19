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
package net.ripe.rpki.validator3.api.slurm;

import net.ripe.rpki.validator3.api.bgpsec.AddBgpSecAssertion;
import net.ripe.rpki.validator3.api.bgpsec.AddBgpSecFilter;
import net.ripe.rpki.validator3.api.bgpsec.BgpSecAssertionsService;
import net.ripe.rpki.validator3.api.bgpsec.BgpSecFilterService;
import net.ripe.rpki.validator3.api.ignorefilters.AddIgnoreFilter;
import net.ripe.rpki.validator3.api.ignorefilters.IgnoreFilterService;
import net.ripe.rpki.validator3.api.roaprefixassertions.AddRoaPrefixAssertion;
import net.ripe.rpki.validator3.api.roaprefixassertions.RoaPrefixAssertionsService;
import net.ripe.rpki.validator3.api.slurm.dtos.Slurm;
import net.ripe.rpki.validator3.api.slurm.dtos.SlurmBgpSecAssertion;
import net.ripe.rpki.validator3.api.slurm.dtos.SlurmBgpSecFilter;
import net.ripe.rpki.validator3.api.slurm.dtos.SlurmLocallyAddedAssertions;
import net.ripe.rpki.validator3.api.slurm.dtos.SlurmOutputFilters;
import net.ripe.rpki.validator3.api.slurm.dtos.SlurmPrefixAssertion;
import net.ripe.rpki.validator3.api.slurm.dtos.SlurmPrefixFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.util.stream.Collectors;

/*
    TODO Add BGPSec-related functionality
 */

@Service
public class SlurmService {

    @Autowired
    private RoaPrefixAssertionsService roaPrefixAssertionsService;

    @Autowired
    private BgpSecAssertionsService bgpSecAssertionsService;

    @Autowired
    private BgpSecFilterService bgpSecFilterService;

    @Autowired
    private IgnoreFilterService ignoreFilterService;

    @Transactional(Transactional.TxType.REQUIRED)
    public void process(final Slurm slurm) {
        if (slurm.getLocallyAddedAssertions() != null && slurm.getLocallyAddedAssertions().getPrefixAssertions() != null) {
            roaPrefixAssertionsService.clear();
            slurm.getLocallyAddedAssertions().getPrefixAssertions().forEach(prefixAsertion -> {
                final AddRoaPrefixAssertion add = AddRoaPrefixAssertion.builder()
                        .asn(prefixAsertion.getAsn() == null ? null : prefixAsertion.getAsn().toString())
                        .prefix(prefixAsertion.getPrefix())
                        .maximumLength(prefixAsertion.getMaxPrefixLength())
                        .comment(prefixAsertion.getComment())
                        .build();
                roaPrefixAssertionsService.execute(add);
            });
        }

        if (slurm.getValidationOutputFilters() != null && slurm.getValidationOutputFilters().getPrefixFilters() != null) {
            ignoreFilterService.clear();
            slurm.getValidationOutputFilters().getPrefixFilters().forEach(prefixFilter -> {
                final AddIgnoreFilter addIgnoreFilter = AddIgnoreFilter.builder()
                        .asn(prefixFilter.getAsn() == null ? null : prefixFilter.getAsn().toString())
                        .prefix(prefixFilter.getPrefix())
                        .comment(prefixFilter.getComment())
                        .build();
                ignoreFilterService.execute(addIgnoreFilter);
            });
        }

        if (slurm.getLocallyAddedAssertions() != null && slurm.getLocallyAddedAssertions().getBgpsecAssertions() != null) {
            bgpSecAssertionsService.clear();
            slurm.getLocallyAddedAssertions().getBgpsecAssertions().forEach(bgpSecAssertion -> {
                AddBgpSecAssertion add = AddBgpSecAssertion.builder()
                        .asn(bgpSecAssertion.getAsn() == null ? null : bgpSecAssertion.getAsn().toString())
                        .publicKey(bgpSecAssertion.getPublicKey())
                        .ski(bgpSecAssertion.getSki())
                        .comment(bgpSecAssertion.getComment())
                        .build();
                bgpSecAssertionsService.execute(add);
            });
        }

        if (slurm.getValidationOutputFilters() != null && slurm.getValidationOutputFilters().getBgpsecFilters() != null) {
            bgpSecFilterService.clear();
            slurm.getValidationOutputFilters().getBgpsecFilters().forEach(bgpSecFilter -> {
                AddBgpSecFilter add = AddBgpSecFilter.builder()
                        .asn(bgpSecFilter.getAsn() == null ? null : bgpSecFilter.getAsn().toString())
                        .ski(bgpSecFilter.getSki())
                        .comment(bgpSecFilter.getComment())
                        .build();
                bgpSecFilterService.execute(add);
            });
        }

    }

    public Slurm get() {
        final Slurm slurm = new Slurm();

        final SlurmLocallyAddedAssertions slurmLocallyAddedAssertions = new SlurmLocallyAddedAssertions();
        slurmLocallyAddedAssertions.setPrefixAssertions(roaPrefixAssertionsService.all().map(a -> {
            final SlurmPrefixAssertion prefixAssertion = new SlurmPrefixAssertion();
            prefixAssertion.setAsn(a.getAsn());
            prefixAssertion.setPrefix(a.getPrefix());
            prefixAssertion.setMaxPrefixLength(a.getMaximumLength());
            prefixAssertion.setComment(a.getComment());
            return prefixAssertion;
        }).collect(Collectors.toList()));

        slurmLocallyAddedAssertions.setBgpsecAssertions(bgpSecAssertionsService.all().map(a -> {
            final SlurmBgpSecAssertion bgpSecAssertion = new SlurmBgpSecAssertion();
            bgpSecAssertion.setAsn(a.getAsn());
            bgpSecAssertion.setSki(a.getSki());
            bgpSecAssertion.setPublicKey(a.getPublicKey());
            bgpSecAssertion.setComment(a.getComment());
            return bgpSecAssertion;
        }).collect(Collectors.toList()));
        slurm.setLocallyAddedAssertions(slurmLocallyAddedAssertions);

        final SlurmOutputFilters filters = new SlurmOutputFilters();
        filters.setPrefixFilters(ignoreFilterService.all().map(f -> {
            final SlurmPrefixFilter prefixFilter = new SlurmPrefixFilter();
            prefixFilter.setAsn(f.getAsn());
            prefixFilter.setPrefix(f.getPrefix());
            prefixFilter.setComment(f.getComment());
            return prefixFilter;
        }).collect(Collectors.toList()));

        filters.setBgpsecFilters(bgpSecFilterService.all().map(f -> {
            final SlurmBgpSecFilter bgpSecFilter = new SlurmBgpSecFilter();
            bgpSecFilter.setAsn(f.getAsn());
            bgpSecFilter.setSki(f.getSki());
            bgpSecFilter.setComment(f.getComment());
            return bgpSecFilter;
        }).collect(Collectors.toList()));

        slurm.setValidationOutputFilters(filters);

        return slurm;
    }
}
