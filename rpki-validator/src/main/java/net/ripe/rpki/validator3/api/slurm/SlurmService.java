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

import net.ripe.rpki.validator3.api.bgpsec.BgpSecAssertionsService;
import net.ripe.rpki.validator3.api.bgpsec.BgpSecFilterService;
import net.ripe.rpki.validator3.api.ignorefilters.IgnoreFilterService;
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

import java.util.stream.Collectors;

/**
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

    @Autowired
    private SlurmStore slurmStore;

    public void process(final Slurm slurm) {
        slurmStore.importSlurm(slurm);
    }

    public Slurm get() {
        return slurmStore.read().toSlurm();
    }


    // TODO It is to be used to migrate data from H2 to slurm.json
    public Slurm getFromDatabase() {
        final Slurm slurm = new Slurm();

        final SlurmLocallyAddedAssertions slurmLocallyAddedAssertions = new SlurmLocallyAddedAssertions();
        slurmLocallyAddedAssertions.setPrefixAssertions(roaPrefixAssertionsService.all().map(a -> {
            final SlurmPrefixAssertion prefixAssertion = new SlurmPrefixAssertion();
            prefixAssertion.setAsn(String.valueOf(a.getAsn()));
            prefixAssertion.setPrefix(a.getPrefix());
            prefixAssertion.setMaxPrefixLength(a.getMaximumLength());
            prefixAssertion.setComment(a.getComment());
            return prefixAssertion;
        }).collect(Collectors.toList()));

        slurmLocallyAddedAssertions.setBgpsecAssertions(bgpSecAssertionsService.all().map(a -> {
            final SlurmBgpSecAssertion bgpSecAssertion = new SlurmBgpSecAssertion();
            bgpSecAssertion.setAsn(String.valueOf(a.getAsn()));
            bgpSecAssertion.setSki(a.getSki());
            bgpSecAssertion.setPublicKey(a.getPublicKey());
            bgpSecAssertion.setComment(a.getComment());
            return bgpSecAssertion;
        }).collect(Collectors.toList()));

        slurm.setLocallyAddedAssertions(slurmLocallyAddedAssertions);

        final SlurmOutputFilters filters = new SlurmOutputFilters();
        filters.setPrefixFilters(ignoreFilterService.all().map(f -> {
            final SlurmPrefixFilter prefixFilter = new SlurmPrefixFilter();
            prefixFilter.setAsn(String.valueOf(f.getAsn()));
            prefixFilter.setPrefix(f.getPrefix());
            prefixFilter.setComment(f.getComment());
            return prefixFilter;
        }).collect(Collectors.toList()));

        filters.setBgpsecFilters(bgpSecFilterService.all().map(f -> {
            final SlurmBgpSecFilter bgpSecFilter = new SlurmBgpSecFilter();
            bgpSecFilter.setAsn(String.valueOf(f.getAsn()));
            bgpSecFilter.setSki(f.getSki());
            bgpSecFilter.setComment(f.getComment());
            return bgpSecFilter;
        }).collect(Collectors.toList()));

        slurm.setValidationOutputFilters(filters);

        return slurm;
    }



}
