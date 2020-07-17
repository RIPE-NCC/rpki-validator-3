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
package net.ripe.rpki.validator3.api;

import com.google.common.collect.ImmutableSortedSet;
import net.ripe.ipresource.IpRange;
import net.ripe.rpki.validator3.domain.ValidatedRoaPrefix;
import net.ripe.rpki.validator3.domain.validation.ValidatedRpkiObjects;
import org.junit.Test;

import java.math.BigInteger;
import java.time.Instant;

import static java.time.temporal.ChronoUnit.DAYS;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

public class SearchTermTest {

    private final ValidatedRoaPrefix prefixTest = ValidatedRoaPrefix.of(null, 0, IpRange.parse("10.0.0.0/8"), 32,
            Instant.now().toEpochMilli(),Instant.now().plus(365, DAYS).toEpochMilli(), BigInteger.ONE,null);
    private final ValidatedRoaPrefix asnTest = ValidatedRoaPrefix.of(null, 3642, null, 32,
            Instant.now().toEpochMilli(),Instant.now().plus(365, DAYS).toEpochMilli(), BigInteger.ONE,null);
    private final ValidatedRoaPrefix genericTest = ValidatedRoaPrefix.of(
            ValidatedRpkiObjects.TrustAnchorData.of(1L, "Bla Anchor"),
            3642,
            null, 32,
            Instant.now().toEpochMilli(),Instant.now().plus(365, DAYS).toEpochMilli(), BigInteger.ONE,
            ImmutableSortedSet.of("Some location"));

    @Test
    public void should_test_asn() {
        assertThat(new SearchTerm("3642").test(asnTest)).isTrue();
        assertThat(new SearchTerm("as3642").test(asnTest)).isTrue();
        assertThat(new SearchTerm("AS3642").test(asnTest)).isTrue();
        assertThat(new SearchTerm("AS364").test(asnTest)).isFalse();
        assertThat(new SearchTerm("364").test(asnTest)).isFalse();
    }

    @Test
    public void should_test_single_address() {
        assertThat(new SearchTerm("10.0.0.0").test(prefixTest)).isTrue();
        assertThat(new SearchTerm("12.0.0.0").test(prefixTest)).isFalse();
    }

    @Test
    public void should_test_prefix() {
        assertThat(new SearchTerm("10.10.0.0/16").test(prefixTest)).isTrue();
        assertThat(new SearchTerm("11.10.0.0/16").test(prefixTest)).isFalse();
    }

    @Test
    public void should_test_generic() {
        assertThat(new SearchTerm("Bla").test(genericTest)).isTrue();
        assertThat(new SearchTerm("bla").test(genericTest)).isFalse();
        assertThat(new SearchTerm("3742").test(genericTest)).isFalse();
        assertThat(new SearchTerm("locati").test(genericTest)).isTrue();
    }


}