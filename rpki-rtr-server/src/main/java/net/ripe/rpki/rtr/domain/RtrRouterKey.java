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
import net.ripe.rpki.rtr.domain.pdus.Pdu;
import net.ripe.rpki.rtr.domain.pdus.ProtocolVersion;
import net.ripe.rpki.rtr.domain.pdus.RouterKeyPdu;
import org.bouncycastle.util.Arrays;

@Value(staticConstructor = "of")
public class RtrRouterKey implements RtrDataUnit {

    byte[] subjectKeyIdentifier;
    byte[] subjectPublicKeyInfo;
    int asn;

    @Override
    public Pdu toPdu(ProtocolVersion protocolVersion, Flags flags) {
        return RouterKeyPdu.of(protocolVersion, flags, subjectKeyIdentifier, subjectPublicKeyInfo, asn);
    }

    @Override
    public int compareToSameType(RtrDataUnit o) {
        final RtrRouterKey that = (RtrRouterKey) o;
        int rc = Integer.compare(this.asn, that.asn);
        if (rc != 0) {
            return rc;
        }

        rc = Arrays.compareUnsigned(subjectKeyIdentifier, that.subjectKeyIdentifier);
        if (rc != 0) {
            return rc;
        }
        
        return Arrays.compareUnsigned(subjectPublicKeyInfo, that.subjectPublicKeyInfo);
    }
}
