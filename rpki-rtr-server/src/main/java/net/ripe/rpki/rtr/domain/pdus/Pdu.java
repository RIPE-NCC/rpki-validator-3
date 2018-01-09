package net.ripe.rpki.rtr.domain.pdus;

import io.netty.buffer.ByteBuf;
import net.ripe.ipresource.Asn;
import net.ripe.ipresource.IpRange;
import net.ripe.ipresource.Ipv4Address;
import net.ripe.ipresource.Ipv6Address;

import java.util.Arrays;

public interface Pdu {
    int PROTOCOL_VERSION = 1;

    void write(Flags flags, ByteBuf out);

    static Pdu prefix(Asn asn, IpRange ipRange, Integer maxLength) {
        if (ipRange.getStart() instanceof Ipv4Address) {
            long address = ((Ipv4Address) ipRange.getStart()).longValue();
            byte[] prefix = new byte[4];
            prefix[0] = (byte) ((address >> 24) & 0xff);
            prefix[1] = (byte) ((address >> 16) & 0xff);
            prefix[2] = (byte) ((address >> 8) & 0xff);
            prefix[3] = (byte) ((address >> 0) & 0xff);
            return IPv4PrefixPdu.of(
                (byte) ipRange.getPrefixLength(),
                (byte) (maxLength != null ? maxLength : ipRange.getPrefixLength()),
                prefix,
                (int) asn.longValue()
            );
        } else {
            Ipv6Address address = (Ipv6Address) ipRange.getStart();
            byte[] bytes = address.getValue().toByteArray();
            byte[] prefix;
            if (bytes.length > 16) {
                prefix = Arrays.copyOfRange(bytes, bytes.length - 16, bytes.length);
            } else if (bytes.length < 16) {
                prefix = new byte[16];
                for (int i = 0; i < bytes.length; ++i) {
                    prefix[i + 16 - bytes.length] = bytes[i];
                }
            } else {
                prefix = bytes;
            }
            return IPv6PrefixPdu.of(
                (byte) ipRange.getPrefixLength(),
                (byte) (maxLength != null ? maxLength : ipRange.getPrefixLength()),
                prefix,
                (int) asn.longValue()
            );
        }
    }
}
