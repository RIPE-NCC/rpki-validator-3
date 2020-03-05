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
package net.ripe.rpki.validator3.storage.encoding.custom;

import net.ripe.ipresource.Asn;
import net.ripe.ipresource.IpRange;
import net.ripe.rpki.validator3.api.bgp.PackedIpRange;
import net.ripe.rpki.validator3.storage.data.RoaPrefix;
import net.ripe.rpki.validator3.storage.encoding.Coder;

import java.math.BigInteger;
import java.util.Map;

public class RoaPrefixCoder implements Coder<RoaPrefix> {

    private final static short PREFIX_TAG = Tags.unique(21);
    private final static short ASN_TAG = Tags.unique(22);
    private final static short MAX_LEN_TAG = Tags.unique(23);
    private final static short VALIDITY_BEFORE = Tags.unique(24);
    private final static short VALIDITY_AFTER = Tags.unique(25);
    private final static short SERIAL_NUMBER = Tags.unique(26);

    @Override
    public byte[] toBytes(RoaPrefix roaPrefix) {
        final Encoded encoded = new Encoded();
        BaseCoder.toBytes(roaPrefix, encoded);
        encoded.append(PREFIX_TAG, new PackedIpRange(roaPrefix.getPrefix()).getContent());
        encoded.append(ASN_TAG, Coders.toBytes(roaPrefix.getAsn()));
        encoded.appendNotNull(MAX_LEN_TAG, roaPrefix.getMaximumLength(), Coders::toBytes);
        encoded.appendNotNull(VALIDITY_BEFORE, roaPrefix.getNotBefore(), Coders::toBytes);
        encoded.appendNotNull(VALIDITY_AFTER, roaPrefix.getNotAfter(), Coders::toBytes);
        encoded.appendNotNull(SERIAL_NUMBER, roaPrefix.getSerialNumber(), Coders::toBytes);
        return encoded.toByteArray();
    }

    @Override
    public RoaPrefix fromBytes(byte[] bytes) {
        Map<Short, byte[]> content = Encoded.fromByteArray(bytes).getContent();
        IpRange prefix = PackedIpRange.of(content.get(PREFIX_TAG));
        long asn = Coders.toLong(content.get(ASN_TAG));
        byte[] maxLen = content.get(MAX_LEN_TAG);
        Integer maximumLength = maxLen != null ? Coders.toInt(maxLen) : null;
        long notBefore = Coders.toLong(content.get(VALIDITY_BEFORE));
        long notAfter = Coders.toLong(content.get(VALIDITY_AFTER));
        BigInteger serialNumber = Coders.toBigInteger(content.get(SERIAL_NUMBER));

        RoaPrefix roaPrefix = RoaPrefix.of(prefix, maximumLength, new Asn(asn),notBefore, notAfter, serialNumber);
        BaseCoder.fromBytes(content, roaPrefix);
        return roaPrefix;
    }

}
