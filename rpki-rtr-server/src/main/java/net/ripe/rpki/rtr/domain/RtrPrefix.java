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
package net.ripe.rpki.rtr.domain;

import lombok.Value;
import net.ripe.rpki.rtr.domain.pdus.Flags;
import net.ripe.rpki.rtr.domain.pdus.IPv4PrefixPdu;
import net.ripe.rpki.rtr.domain.pdus.IPv6PrefixPdu;
import net.ripe.rpki.rtr.domain.pdus.Pdu;
import net.ripe.rpki.rtr.domain.pdus.ProtocolVersion;
import org.bouncycastle.util.Arrays;

@Value(staticConstructor = "of")
public class RtrPrefix implements RtrDataUnit {
    byte prefixLength;
    byte maxLength;
    byte[] prefix;
    int asn;

    @Override
    public Pdu toPdu(ProtocolVersion protocolVersion, Flags flags) {
        if (prefix.length == 4) {
            return IPv4PrefixPdu.of(protocolVersion, flags, this);
        } else if (prefix.length == 16) {
            return IPv6PrefixPdu.of(protocolVersion, flags, this);
        } else {
            throw new IllegalStateException(String.format("invalid RTR prefix length, expected 4 or 16, was %d", prefix.length));
        }
    }

    @Override
    public int compareToSameType(RtrDataUnit o) {
        final RtrPrefix that = (RtrPrefix) o;
        int rc = Integer.compare(this.prefix.length, that.prefix.length);
        if (rc != 0) {
            return rc;
        }

        rc = Arrays.compareUnsigned(this.prefix, that.prefix);
        if (rc != 0) {
            return rc;
        }

        rc = Integer.compareUnsigned(this.prefixLength, that.prefixLength);
        if (rc != 0) {
            return rc;
        }

        rc = Integer.compareUnsigned(this.maxLength, that.maxLength);
        if (rc != 0) {
            return rc;
        }

        return Integer.compareUnsigned(this.asn, that.asn);
    }
}
