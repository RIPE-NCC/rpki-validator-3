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
