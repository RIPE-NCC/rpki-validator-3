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
package net.ripe.rpki.validator3.domain.validation;

import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import net.ripe.ipresource.Asn;
import net.ripe.ipresource.IpRange;
import net.ripe.ipresource.Ipv4Address;
import net.ripe.ipresource.Ipv6Address;
import net.ripe.rpki.validator3.api.bgp.BgpPreviewService;
import org.junit.Assume;
import org.junit.runner.RunWith;

import java.math.BigInteger;

import static org.hamcrest.number.OrderingComparison.greaterThan;
import static org.hamcrest.number.OrderingComparison.greaterThanOrEqualTo;
import static org.hamcrest.number.OrderingComparison.lessThan;
import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeThat;

@RunWith(JUnitQuickcheck.class)
public class BgpPreviewEntryTest {
    @Property(trials = 1000)
    public void should_store_ipv4_prefix_efficiently(int start) {
        long value = Integer.toUnsignedLong(start);
        int prefixLength = 32 - (Long.numberOfTrailingZeros((1L << 32) + value));
        IpRange prefix = IpRange.prefix(new Ipv4Address(value), prefixLength);

        BgpPreviewService.BgpPreviewEntry entry = BgpPreviewService.BgpPreviewEntry.of(Asn.parse("AS1"), prefix, BgpPreviewService.Validity.VALID);

        assertEquals(prefix, entry.getPrefix());
    }

    private static BigInteger maxIpv6 = BigInteger.valueOf(2L).pow(128).subtract(BigInteger.ONE);

    @Property(trials = 1000)
    public void should_store_ipv6_prefix_efficiently(BigInteger value) {
        assumeThat(value, greaterThanOrEqualTo(BigInteger.ZERO));
        assumeThat(value, lessThan(maxIpv6));

        int prefixLength = 128 - (value.getLowestSetBit() == -1 ? 0 : value.getLowestSetBit());
        IpRange prefix = IpRange.prefix(new Ipv6Address(value), prefixLength);

        BgpPreviewService.BgpPreviewEntry entry = BgpPreviewService.BgpPreviewEntry.of(Asn.parse("AS1"), prefix, BgpPreviewService.Validity.VALID);

        assertEquals(prefix, entry.getPrefix());
    }
}
