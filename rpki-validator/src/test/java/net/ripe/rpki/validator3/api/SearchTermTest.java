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

import net.ripe.ipresource.Asn;
import net.ripe.ipresource.IpRange;
import net.ripe.rpki.validator3.domain.ValidatedRpkiObjects.RoaPrefix;
import org.junit.Test;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

public class SearchTermTest {

    final RoaPrefix prefixTest = RoaPrefix.of(null, null, IpRange.parse("10.0.0.0/8"), 32, 32, null);
    final RoaPrefix asnTest = RoaPrefix.of(null,  Asn.parse("3642"), null, 32, 32, null);


    @Test
    public void should_accept_matching_asn(){
        SearchTerm validASN = new SearchTerm("3642");
        validASN.test(asnTest);
        assertThat(validASN.test(asnTest)).isTrue();
    }

    @Test
    public void should_accept_matching_prefix(){
        SearchTerm searchPrefix = new SearchTerm("10.0.0.0");
        searchPrefix.test(prefixTest);
        assertThat(searchPrefix.test(prefixTest)).isTrue();
    }

    @Test
    public void should_reject_mismatched_asn(){
        SearchTerm validASN = new SearchTerm("1111");
        validASN.test(asnTest);
        assertThat(validASN.test(asnTest)).isFalse();
    }

    @Test
    public void should_reject_non_overlapping_prefix(){
        SearchTerm searchPrefix = new SearchTerm("11.0.0.0");
        searchPrefix.test(prefixTest);
        assertThat(searchPrefix.test(prefixTest)).isFalse();
    }

}