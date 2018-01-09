package net.ripe.rpki.rtr.domain.pdus;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import net.ripe.ipresource.Asn;
import net.ripe.ipresource.IpRange;
import net.ripe.ipresource.Ipv4Address;
import net.ripe.ipresource.Ipv6Address;
import org.assertj.core.util.Strings;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class PduTest {

    @Test
    public void should_write_ipv4_prefix_pdu() {
        // https://tools.ietf.org/html/rfc8210#section-5.6
        assertThat(write(Flags.ANNOUNCEMENT, Pdu.prefix(Asn.parse("3333"), IpRange.prefix(Ipv4Address.parse("127.0.0.0"), 8), 18))).isEqualToIgnoringWhitespace(Strings.concat(
            "01 04 00 00",
            "00 00 00 14",
            "01 08 12 00",
            "7f 00 00 00",
            "00 00 0d 05"
        ));

        assertThat(write(Flags.WITHDRAWAL, Pdu.prefix(Asn.parse("" + Asn.ASN32_MAX_VALUE), IpRange.prefix(Ipv4Address.parse("255.255.255.255"), 32), null))).isEqualToIgnoringWhitespace(Strings.concat(
            "01 04 00 00",
            "00 00 00 14",
            "00 20 20 00",
            "ff ff ff ff",
            "ff ff ff ff"
        ));
    }

    @Test
    public void should_write_ipv6_prefix_pdu() {
        assertThat(write(Flags.ANNOUNCEMENT, Pdu.prefix(Asn.parse("3333"), IpRange.prefix(Ipv6Address.parse("2001:67c:2e8:110::"), 64), 80))).isEqualToIgnoringWhitespace(Strings.concat(
            "01 06 00 00",
            "00 00 00 20",
            "01 40 50 00",
            "20 01 06 7c 02 e8 01 10 00 00 00 00 00 00 00 00",
            "00 00 0d 05"
        ));

        assertThat(write(Flags.WITHDRAWAL, Pdu.prefix(Asn.parse("" + Asn.ASN32_MAX_VALUE), IpRange.prefix(Ipv6Address.parse("::1"), 128), null))).isEqualToIgnoringWhitespace(Strings.concat(
            "01 06 00 00",
            "00 00 00 20",
            "00 80 80 00",
            "00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01",
            "ff ff ff ff"
        ));
    }

    private String write(Flags flags, Pdu pdu) {
        ByteBuf buffer = Unpooled.buffer();
        pdu.write(flags, buffer);
        return ByteBufUtil.hexDump(buffer);
    }
}
