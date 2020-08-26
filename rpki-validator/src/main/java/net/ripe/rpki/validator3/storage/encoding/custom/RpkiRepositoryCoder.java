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

import net.ripe.rpki.validator3.storage.data.Ref;
import net.ripe.rpki.validator3.storage.data.RpkiRepository;
import net.ripe.rpki.validator3.storage.data.TrustAnchor;
import net.ripe.rpki.validator3.storage.encoding.Coder;

import java.util.HashSet;
import java.util.List;
import java.util.Map;

public class RpkiRepositoryCoder implements Coder<RpkiRepository> {

    private final static short TYPE_TAG = Tags.unique(51);
    private final static short RRDP_NOTIFY_URL_TAG = Tags.unique(52);
    private final static short RSYNC_URL_TAG = Tags.unique(53);
    private final static short STATUS_TAG = Tags.unique(54);
    private final static short RRDP_SERIAL = Tags.unique(55);
    private final static short RRDP_SESSION = Tags.unique(56);
    private final static short LAST_DOWNLOADED = Tags.unique(57);
    private final static short PARENT_REPOSITORY = Tags.unique(58);
    private final static short TRUST_ANCHORS = Tags.unique(59);

    private final static RefCoder<RpkiRepository> repoRefCoder = new RefCoder<>();
    private final static RefCoder<TrustAnchor> taRefCoder = new RefCoder<>();

    @Override
    public byte[] toBytes(RpkiRepository rpkiRepository) {
        final Encoded encoded = new Encoded();

        BaseCoder.toBytes(rpkiRepository, encoded);

        encoded.append(TYPE_TAG, Coders.toBytes(rpkiRepository.getType().name()));
        encoded.append(STATUS_TAG, Coders.toBytes(rpkiRepository.getStatus().name()));
        encoded.appendNotNull(RRDP_NOTIFY_URL_TAG, rpkiRepository.getRrdpNotifyUri(), Coders::toBytes);
        encoded.appendNotNull(RSYNC_URL_TAG, rpkiRepository.getRsyncRepositoryUri(), Coders::toBytes);
        encoded.appendNotNull(RRDP_SESSION, rpkiRepository.getRrdpSessionId(), Coders::toBytes);
        encoded.appendNotNull(RRDP_SERIAL, rpkiRepository.getRrdpSerial(), Coders::toBytes);
        encoded.appendNotNull(LAST_DOWNLOADED, rpkiRepository.getLastDownloadedAt(), Coders::toBytes);

        if (rpkiRepository.getTrustAnchors() != null && !rpkiRepository.getTrustAnchors().isEmpty()) {
            byte[] taBytes = Coders.toBytes(rpkiRepository.getTrustAnchors(), taRefCoder::toBytes);
            encoded.append(TRUST_ANCHORS, taBytes);
        }

        return encoded.toByteArray();
    }

    @Override
    public RpkiRepository fromBytes(byte[] bytes) {
        Map<Short, byte[]> content = Encoded.fromByteArray(bytes).getContent();

        final RpkiRepository rpkiRepository = new RpkiRepository();
        BaseCoder.fromBytes(content, rpkiRepository);

        rpkiRepository.setType(RpkiRepository.Type.valueOf(Coders.toString(content.get(TYPE_TAG))));
        rpkiRepository.setStatus(Coders.toString(content.get(STATUS_TAG)));
        Encoded.field(content, RRDP_NOTIFY_URL_TAG).ifPresent(b -> rpkiRepository.setRrdpNotifyUri(Coders.toString(b)));
        Encoded.field(content, RSYNC_URL_TAG).ifPresent(b -> rpkiRepository.setRsyncRepositoryUri(Coders.toString(b)));
        Encoded.field(content, RRDP_SESSION).ifPresent(b -> rpkiRepository.setRrdpSessionId(Coders.toString(b)));
        Encoded.field(content, RRDP_SERIAL).ifPresent(b -> rpkiRepository.setRrdpSerial(Coders.toBigInteger(b)));
        Encoded.field(content, LAST_DOWNLOADED).ifPresent(b -> rpkiRepository.setLastDownloadedAt(Coders.toInstant(b)));

        Encoded.field(content, TRUST_ANCHORS).ifPresent(b -> {
            final List<Ref<TrustAnchor>> objects = Coders.fromBytes(b, taRefCoder::fromBytes);
            rpkiRepository.setTrustAnchors(new HashSet<>(objects));
        });

        return rpkiRepository;
    }

}
