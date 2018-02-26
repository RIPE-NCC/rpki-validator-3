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
import net.ripe.rpki.validator3.domain.RpkiObjects;

public class SearchTerm {
    private final String term;
    private final IpRange range ;
    private final Asn asn;

    public SearchTerm(String term) {
        this.term = term;
        this.range = convertIpRange();
        this.asn = convertAsn();
    }

    public Asn asAsn() {
        return asn;
    }

    public IpRange asIpRange() {
        return range;
    }

    public String asString() {
        return term;
    }

    public boolean match(RpkiObjects.RoaPrefix prefix) {
        if (asn != null && asn.equals(prefix.getAsn())) {
            return true;
        }
        if (range != null && range.overlaps(prefix.getPrefix())) {
            return true;
        }
        return prefix.getTrustAnchor().contains(term) || prefix.getUri().contains(term);
    }

    private Asn convertAsn() {
        try {
            return Asn.parse(term);
        } catch (Exception e) {
            return null;
        }
    }

    private IpRange convertIpRange() {
        try {
            return IpRange.parse(term);
        } catch (Exception e) {
            return null;
        }
    }
}
