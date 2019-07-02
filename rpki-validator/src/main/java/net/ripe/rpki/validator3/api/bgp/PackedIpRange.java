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
package net.ripe.rpki.validator3.api.bgp;

import lombok.Getter;
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
    @Getter
    private byte[] content;

    public PackedIpRange(IpRange ipRange) {
        if (ipRange.getType() == IPv4) {
            long start = ipRange.getStart().getValue().longValue();
            long end = ipRange.getEnd().getValue().longValue();
            content = new byte[8];
            toBytes(start, 0, content);
            toBytes(end, 4, content);
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

    IpRange toIpRange() {
        return of(content);
    }

    public static IpRange of(byte[] content) {
        if (content.length == 8) {
            // it's IPv4
            long s = fromBytes(content, 0);
            long e = fromBytes(content, 4);
            return IpRange.range(new Ipv4Address(s), new Ipv4Address(e));
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

    private static long fromBytes(byte[] b, int offset) {
        long s = 0L;
        for (short i = 0; i < 3; i++) {
            s |= b[i + offset] & 0xFF;
            s <<= 8;
        }
        s |= b[3 + offset] & 0xFF;
        return s;
    }

    private static void toBytes(long s, int offset, byte[] b) {
        b[offset] = (byte) (s >>> 24);
        b[offset + 1] = (byte) (s >>> 16);
        b[offset + 2] = (byte) (s >>> 8);
        b[offset + 3] = (byte) s;
    }
}
