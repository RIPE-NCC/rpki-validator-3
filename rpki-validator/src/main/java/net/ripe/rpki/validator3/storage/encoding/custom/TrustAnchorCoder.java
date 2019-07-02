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

import net.ripe.rpki.validator3.storage.data.TrustAnchor;
import net.ripe.rpki.validator3.storage.encoding.Coder;

import java.util.Map;

import static net.ripe.rpki.validator3.storage.encoding.custom.Encoded.field;

public class TrustAnchorCoder implements Coder<TrustAnchor> {

    private final static short NAME_TAG = Tags.unique(71);
    private final static short RSYNC_PREFETCH_TAG = Tags.unique(72);
    private final static short ENCODED_CERT_TAG = Tags.unique(73);
    private final static short LOCATIONS_TAG = Tags.unique(74);
    private final static short SPKI_SERIAL = Tags.unique(75);
    private final static short PRECONFIGURED_TAG = Tags.unique(76);
    private final static short INITIAL_VALIDATION_DONE_TAG = Tags.unique(77);

    @Override
    public byte[] toBytes(TrustAnchor trustAnchor) {
        final Encoded encoded = new Encoded();

        BaseCoder.toBytes(trustAnchor, encoded);

        encoded.append(NAME_TAG, Coders.toBytes(trustAnchor.getName()));
        encoded.appendNotNull(ENCODED_CERT_TAG, trustAnchor.getEncodedCertificate(), c -> c);
        encoded.appendNotNull(SPKI_SERIAL, trustAnchor.getSubjectPublicKeyInfo(), Coders::toBytes);
        encoded.appendNotNull(RSYNC_PREFETCH_TAG, trustAnchor.getRsyncPrefetchUri(), Coders::toBytes);
        encoded.appendNotNull(PRECONFIGURED_TAG, trustAnchor.isPreconfigured(), Coders::toBytes);
        encoded.appendNotNull(INITIAL_VALIDATION_DONE_TAG, trustAnchor.isInitialCertificateTreeValidationRunCompleted(),
                Coders::toBytes);

        if (trustAnchor.getLocations() != null && !trustAnchor.getLocations().isEmpty()) {
            encoded.append(LOCATIONS_TAG, Coders.toBytes(trustAnchor.getLocations(), Coders::toBytes));
        }

        return encoded.toByteArray();
    }

    @Override
    public TrustAnchor fromBytes(byte[] bytes) {
        Map<Short, byte[]> content = Encoded.fromByteArray(bytes).getContent();

        final TrustAnchor trustAnchor = new TrustAnchor();
        BaseCoder.fromBytes(content, trustAnchor);

        trustAnchor.setName(Coders.toString(content.get(NAME_TAG)));
        field(content, ENCODED_CERT_TAG).ifPresent(trustAnchor::setEncodedCertificate);
        field(content, SPKI_SERIAL).ifPresent(b -> trustAnchor.setSubjectPublicKeyInfo(Coders.toString(b)));
        field(content, RSYNC_PREFETCH_TAG).ifPresent(b -> trustAnchor.setRsyncPrefetchUri(Coders.toString(b)));
        field(content, PRECONFIGURED_TAG).ifPresent(b -> trustAnchor.setPreconfigured(Coders.toBoolean(b)));
        field(content, INITIAL_VALIDATION_DONE_TAG).ifPresent(b ->
                trustAnchor.setInitialCertificateTreeValidationRunCompleted(Coders.toBoolean(b)));

        field(content, LOCATIONS_TAG).ifPresent(b ->
                trustAnchor.setLocations(Coders.fromBytes(b, Coders::toString)));

        return trustAnchor;
    }

}
