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
package net.ripe.rpki.validator3.domain;

import net.ripe.ipresource.Asn;
import net.ripe.ipresource.IpRange;
import net.ripe.ipresource.IpResourceSet;
import net.ripe.ipresource.etree.IntervalMap;
import net.ripe.ipresource.etree.IpResourceIntervalStrategy;
import net.ripe.ipresource.etree.NestedIntervalMap;

import java.util.function.Predicate;
import java.util.stream.Stream;

public class IgnoreFiltersPredicate implements Predicate<RoaPrefixDefinition> {

    private IpResourceSet ignoredAsns = new IpResourceSet();
    private IntervalMap<IpRange, IpResourceSet> ignoredPrefixes = new NestedIntervalMap<>(IpResourceIntervalStrategy.getInstance());

    public IgnoreFiltersPredicate(Stream<IgnoreFilter> ignoreFilterStream) {
        ignoreFilterStream.forEach(filter -> {
            if (filter.getPrefix() == null) {
                ignoredAsns.add(new Asn(filter.getAsn()));
            } else {
                IpRange prefix = IpRange.parse(filter.getPrefix());
                IpResourceSet existing = ignoredPrefixes.findExact(prefix);
                if (existing == null) {
                    existing = new IpResourceSet();
                    ignoredPrefixes.put(prefix, existing);
                }
                if (filter.getAsn() == null) {
                    existing.add(new Asn(Asn.ASN_MIN_VALUE).upTo(new Asn(Asn.ASN32_MAX_VALUE)));
                } else {
                    existing.add(new Asn(filter.getAsn()));
                }
            }
        });
    }

    @Override
    public boolean test(RoaPrefixDefinition roaPrefix) {
        if (ignoredAsns.contains(roaPrefix.getAsn())) {
            return true;
        }
        IpResourceSet filter = ignoredPrefixes.findExactOrFirstLessSpecific(roaPrefix.getPrefix());
        if (filter != null && filter.contains(roaPrefix.getAsn())) {
            return true;
        }
        return false;
    }
}
