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

import net.ripe.rpki.validator3.api.slurm.dtos.Slurm;
import net.ripe.rpki.validator3.api.slurm.dtos.SlurmBgpSecAssertion;
import net.ripe.rpki.validator3.api.slurm.dtos.SlurmBgpSecFilter;
import net.ripe.rpki.validator3.api.slurm.dtos.SlurmPrefixAssertion;
import net.ripe.rpki.validator3.api.slurm.dtos.SlurmPrefixFilter;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

public class SlurmParserTest {

    @Test
    public void parse() throws IOException {
        final Slurm slurm = SlurmParser.parse(read("slurm/slurm1.json"));

        final List<SlurmPrefixFilter> prefixFilters = slurm.getValidationOutputFilters().getPrefixFilters();
        assertEquals(null, prefixFilters.get(0).getAsn());
        assertEquals(new Long(64496), prefixFilters.get(1).getAsn());
        assertEquals(new Long(64497), prefixFilters.get(2).getAsn());
        assertEquals("198.51.100.0/24", prefixFilters.get(2).getPrefix());
        assertEquals("All VRPs encompassed by prefix, matching ASN", prefixFilters.get(2).getComment());

        final List<SlurmBgpSecFilter> bgpsecFilters = slurm.getValidationOutputFilters().getBgpsecFilters();
        assertEquals(new Long(64496), bgpsecFilters.get(0).getAsn());
        assertEquals(null, bgpsecFilters.get(1).getAsn());
        assertEquals("Zm9v", bgpsecFilters.get(1).getRouterSKI());
        assertEquals(new Long(64497), bgpsecFilters.get(2).getAsn());
        assertEquals("YmFy", bgpsecFilters.get(2).getRouterSKI());
        assertEquals("Key for ASN 64497 matching Router SKI", bgpsecFilters.get(2).getComment());

        final List<SlurmPrefixAssertion> prefixAssertions = slurm.getLocallyAddedAssertions().getPrefixAssertions();
        assertEquals(new Long(64496), prefixAssertions.get(0).getAsn());
        assertEquals("198.51.100.0/24", prefixAssertions.get(0).getPrefix());
        assertEquals("My other important route", prefixAssertions.get(0).getComment());
        assertEquals(null, prefixAssertions.get(0).getMaxPrefixLength());
        assertEquals(new Long(64496), prefixAssertions.get(1).getAsn());
        assertEquals("2001:DB8::/32", prefixAssertions.get(1).getPrefix());
        assertEquals(new Integer(48), prefixAssertions.get(1).getMaxPrefixLength());
        assertEquals("My other important de-aggregated routes", prefixAssertions.get(1).getComment());

        final List<SlurmBgpSecAssertion> bgpsecAssertions = slurm.getLocallyAddedAssertions().getBgpsecAssertions();
        assertEquals(new Long(64496), bgpsecAssertions.get(0).getAsn());
        assertEquals("<some base64 SKI>", bgpsecAssertions.get(0).getSki());
        assertEquals("<some base64 public key>", bgpsecAssertions.get(0).getPublicKey());
        assertEquals("My known key for my important ASN", bgpsecAssertions.get(0).getComment());
    }

    static String read(final String path) throws IOException {
        final InputStream resource = Thread.currentThread().getContextClassLoader().getResourceAsStream(path);
        try (BufferedReader buffer = new BufferedReader(new InputStreamReader(resource))) {
            return buffer.lines().collect(Collectors.joining("\n"));
        }
    }
}