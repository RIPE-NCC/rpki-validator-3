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

import net.ripe.rpki.validator3.storage.data.RpkiObject;
import net.ripe.rpki.validator3.storage.encoding.Coder;

import java.util.List;
import java.util.Map;
import java.util.TreeSet;

public class RpkiObjectCoder implements Coder<RpkiObject> {

    private final static short TYPE_TAG = Tags.unique(31);
    private final static short SHA256_TAG = Tags.unique(32);
    private final static short AKI_TAG = Tags.unique(33);
    private final static short LAST_MARKED_TAG = Tags.unique(34);
    private final static short SERIAL_TAG = Tags.unique(35);
    private final static short ENCODED_TAG = Tags.unique(36);
    private final static short SIGNING_TIME_TAG = Tags.unique(37);
    private final static short LOCATIONS_TAG = Tags.unique(38);
    private final static short ROA_PREFIXES = Tags.unique(39);

    private final static RoaPrefixCoder roaPrefixCoder = new RoaPrefixCoder();

    @Override
    public byte[] toBytes(RpkiObject rpkiObject) {
        final Encoded encoded = new Encoded();

        BaseCoder.toBytesNoId(rpkiObject, encoded);

        encoded.append(TYPE_TAG, Coders.toBytes(rpkiObject.getType().name()));
        encoded.append(SHA256_TAG, rpkiObject.getSha256());
        encoded.append(AKI_TAG, rpkiObject.getAuthorityKeyIdentifier());
        encoded.appendNotNull(SERIAL_TAG, rpkiObject.getSerialNumber(), Coders::toBytes);
        encoded.appendNotNull(ENCODED_TAG, rpkiObject.getEncoded());
        encoded.appendNotNull(SIGNING_TIME_TAG, rpkiObject.getSigningTime(), Coders::toBytes);

        if (rpkiObject.getRoaPrefixes() != null && !rpkiObject.getRoaPrefixes().isEmpty()) {
            byte[] prefixesBytes = Coders.toBytes(rpkiObject.getRoaPrefixes(), roaPrefixCoder::toBytes);
            encoded.append(ROA_PREFIXES, prefixesBytes);
        }

        return encoded.toByteArray();
    }

    @Override
    public RpkiObject fromBytes(byte[] bytes) {
        Map<Short, byte[]> content = Encoded.fromByteArray(bytes).getContent();

        final RpkiObject rpkiObject = new RpkiObject();
        BaseCoder.fromBytesNoId(content, rpkiObject);

        rpkiObject.setType(RpkiObject.Type.valueOf(Coders.toString(content.get(TYPE_TAG))));
        rpkiObject.setSha256(content.get(SHA256_TAG));
        rpkiObject.setEncoded(content.get(ENCODED_TAG));
        rpkiObject.setAuthorityKeyIdentifier(content.get(AKI_TAG));
        Encoded.field(content, SIGNING_TIME_TAG).ifPresent(b -> rpkiObject.setSigningTime(Coders.toInstant(b)));
        Encoded.field(content, SERIAL_TAG).ifPresent(b -> rpkiObject.setSerialNumber(Coders.toBigInteger(b)));

        Encoded.field(content, ROA_PREFIXES).ifPresent(b ->
                rpkiObject.setRoaPrefixes(Coders.fromBytes(b, roaPrefixCoder::fromBytes)));

        return rpkiObject;
    }

}
