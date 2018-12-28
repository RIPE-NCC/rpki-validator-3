package net.ripe.rpki.validator3.api.bgp;

import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import net.ripe.ipresource.IpRange;
import net.ripe.ipresource.Ipv4Address;
import net.ripe.ipresource.Ipv6Address;
import org.junit.runner.RunWith;

import java.math.BigInteger;

import static org.hamcrest.number.OrderingComparison.greaterThan;
import static org.hamcrest.number.OrderingComparison.lessThan;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeThat;


@RunWith(JUnitQuickcheck.class)
public class PackedIpRangeTest {

    private static BigInteger maxIpv6 = BigInteger.valueOf(2L).pow(128).subtract(BigInteger.ONE);

    @Property(trials = 1000)
    public void packUnpackIpv4(int s, int e) throws Exception {
        assumeThat(s, greaterThan(0));
        assumeThat(e, greaterThan(s));
        IpRange range = IpRange.range(new Ipv4Address(s), new Ipv4Address(e));
        IpRange unpacked = new PackedIpRange(range).toIpRange();
        assertEquals(range, unpacked);
    }

    @Property(trials = 1000)
    public void packUnpackIpv6(BigInteger s, BigInteger e) throws Exception {
        assumeThat(s, greaterThan(BigInteger.ZERO));
        assumeThat(e, greaterThan(s));
        assumeThat(s, lessThan(maxIpv6));
        assumeThat(e, lessThan(maxIpv6));
        IpRange range = IpRange.range(new Ipv6Address(s), new Ipv6Address(e));
        IpRange unpacked = new PackedIpRange(range).toIpRange();
        assertEquals(range, unpacked);
    }

}
