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
package net.ripe.rpki.rtr.domain.pdus;

import io.netty.buffer.ByteBuf;
import net.ripe.ipresource.Asn;
import net.ripe.ipresource.IpRange;
import net.ripe.ipresource.Ipv4Address;
import net.ripe.ipresource.Ipv6Address;

import java.util.Arrays;

public interface Pdu {
    int PROTOCOL_VERSION = 1;

    void write(ByteBuf out);

    int length();

    static Pdu prefix(Flags flags, Asn asn, IpRange ipRange, Integer maxLength) {
        if (ipRange.getStart() instanceof Ipv4Address) {
            long address = ((Ipv4Address) ipRange.getStart()).longValue();
            byte[] prefix = new byte[4];
            prefix[0] = (byte) ((address >> 24) & 0xff);
            prefix[1] = (byte) ((address >> 16) & 0xff);
            prefix[2] = (byte) ((address >> 8) & 0xff);
            prefix[3] = (byte) ((address >> 0) & 0xff);
            return IPv4PrefixPdu.of(
                flags,
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
                flags,
                (byte) ipRange.getPrefixLength(),
                (byte) (maxLength != null ? maxLength : ipRange.getPrefixLength()),
                prefix,
                (int) asn.longValue()
            );
        }
    }
}
