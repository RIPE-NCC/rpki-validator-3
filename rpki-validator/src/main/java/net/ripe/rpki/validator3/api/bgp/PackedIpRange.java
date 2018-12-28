package net.ripe.rpki.validator3.api.bgp;

import net.ripe.ipresource.IpRange;
import net.ripe.ipresource.Ipv4Address;
import net.ripe.ipresource.Ipv6Address;

import java.math.BigInteger;
import java.nio.ByteBuffer;

import static net.ripe.ipresource.IpResourceType.IPv4;
import static net.ripe.ipresource.IpResourceType.IPv6;

/**
 * An experimental memory optimised way of storing IP ranges.
 */
public class PackedIpRange {
    private byte[] content;

    public PackedIpRange(IpRange ipRange) {
        if (ipRange.getType() == IPv4) {
            int start = ipRange.getStart().getValue().intValue();
            int end = ipRange.getEnd().getValue().intValue();
            content = ByteBuffer.allocate(8)
                    .putInt(start)
                    .putInt(end).array();
        } else if (ipRange.getType() == IPv6) {
            byte[] start = ipRange.getStart().getValue().toByteArray();
            byte[] end = ipRange.getEnd().getValue().toByteArray();
            int wholeLen = start.length + end.length + 2;

            // never make it 8 because that's the only way to distinguish between IPv4 and IPv6
            if (wholeLen == 8) {
                wholeLen++;
            }
            content = ByteBuffer.allocate(wholeLen)
                    .put((byte) start.length)
                    .put(start)
                    .put((byte) end.length)
                    .put(end)
                    .array();
        } else {
            throw new IllegalArgumentException("Asn is not supported here");
        }
    }

    public IpRange toIpRange() {
        if (content.length == 8) {
            // it's IPv4
            ByteBuffer byteBuffer = ByteBuffer.wrap(content);
            int start = byteBuffer.getInt(0);
            int end = byteBuffer.getInt(4);
            return IpRange.range(new Ipv4Address(start), new Ipv4Address(end));
        } else {
            // it's IPv6
            final ByteBuffer byteBuffer = ByteBuffer.wrap(content);

            final int sLen = byteBuffer.get();
            byte[] tmp = new byte[sLen];
            byteBuffer.get(tmp, 0, sLen);
            final BigInteger start = new BigInteger(tmp);

            final int eLen = byteBuffer.get();
            tmp = new byte[eLen];
            byteBuffer.get(tmp, 0, eLen);

            final BigInteger end = new BigInteger(tmp);
            return IpRange.range(new Ipv6Address(start), new Ipv6Address(end));
        }
    }
}
