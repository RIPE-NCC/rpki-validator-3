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
import net.ripe.ipresource.IpAddress;
import net.ripe.ipresource.IpRange;
import net.ripe.rpki.validator3.domain.ValidatedRpkiObjects;

import java.util.function.Predicate;

import static net.ripe.ipresource.IpResourceType.IPv4;

public class SearchTerm implements Predicate<ValidatedRpkiObjects.RoaPrefix> {
    private final String term;
    private final IpRange range;
    private final Asn asn;

    private final static int IPv4_PREFIX_LENGTH = 32;
    private final static int IPv6_PREFIX_LENGTH = 128;

    public SearchTerm(String term) {
        this.term = term == null ? "" : term.trim();
        this.range = convertPrefix(term);
        this.asn = convertAsn(term);
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

    public boolean test(ValidatedRpkiObjects.RoaPrefix prefix) {
        if (asn != null){
          return asn.equals(prefix.getAsn());
        }

        if (range != null){
            return range.overlaps(prefix.getPrefix());
        }

        return  prefix.getTrustAnchor().getName().contains(term) ||
                prefix.getLocations().stream().anyMatch(uri -> uri.contains(term));
    }

    private Asn convertAsn(String value) {
        try {
            return Asn.parse(value);
        } catch (Exception e) {
            return null;
        }
    }

    private IpRange convertRange(String value) {
        try {
            return IpRange.parse(value);
        } catch (Exception e) {
            return null;
        }
    }

    private IpRange convertIpAddress(String value) {
        try {
            IpAddress ipAddress = IpAddress.parse(value);
            // single IPv4 address treat it like a /32 and IPv6 like /128
            if (IPv4.equals(ipAddress.getType())) {
                return IpRange.prefix(ipAddress, IPv4_PREFIX_LENGTH);
            } else {
                return IpRange.prefix(ipAddress, IPv6_PREFIX_LENGTH);
            }
        } catch (Exception e) {
            return null;
        }
    }

    private IpRange convertPrefix(String value) {
        IpRange range = convertRange(value);
        // in case value is single IP address e.g. 1.0.0.2 without suffix e.g. /24
        if (range == null) {
            return convertIpAddress(value);
        }
        return range;
    }
}
