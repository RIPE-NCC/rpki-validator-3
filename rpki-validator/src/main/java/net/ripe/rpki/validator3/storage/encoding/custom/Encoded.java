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

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

@Data
@AllArgsConstructor
@EqualsAndHashCode
public class Encoded {

    private final Map<Short, byte[]> content = new HashMap<>();

    public void append(short fieldTag, byte[] bytes) {
        if (bytes != null) {
            content.put(fieldTag, bytes);
        }
    }

    public <T> void appendNotNull(short fieldTag, T value, Function<T, byte[]> f) {
        if (value != null) {
            content.put(fieldTag, f.apply(value));
        }
    }

    public void appendNotNull(short fieldTag, byte[] bytes) {
        if (bytes != null) {
            content.put(fieldTag, bytes);
        }
    }

    public byte[] toByteArray() {
        int totalSize = Integer.BYTES +
                content.values().stream()
                .map(b -> Short.BYTES + Integer.BYTES + b.length)
                .reduce(0, Integer::sum);

        byte[] array = new byte[totalSize];
        ByteBuffer bb = ByteBuffer.wrap(array);
        final int entryCount = content.size();
        bb.putInt(entryCount);

        int currentOffset = 0;
        byte[][] bbytes = new byte[entryCount][];
        int i = 0;
        for (Map.Entry<Short, byte[]> e : content.entrySet()) {
            byte[] bytes = e.getValue();
            bb.putShort(e.getKey());
            bb.putInt(currentOffset);
            currentOffset += bytes.length;
            bbytes[i++] = bytes;
        }
        for (int k = 0; k < entryCount; k++) {
            bb.put(bbytes[k]);
        }
        return array;
    }

    public static Encoded fromByteArray(byte[] array) {
        final ByteBuffer bb = ByteBuffer.wrap(array);
        final int entryCount = bb.getInt();
        final Encoded encoded = new Encoded();
        if (entryCount != 0) {
            short[] tags = new short[entryCount];
            int[] offsets = new int[entryCount];
            for (int i = 0; i < entryCount; i++) {
                tags[i] = bb.getShort();
                offsets[i] = bb.getInt();
            }
            for (int i = 0; i < entryCount - 1; i++) {
                byte[] bytes = new byte[offsets[i + 1] - offsets[i]];
                bb.get(bytes);
                encoded.append(tags[i], bytes);
            }
            byte[] bytes = new byte[bb.remaining()];
            bb.get(bytes);
            encoded.append(tags[entryCount - 1], bytes);
        }
        return encoded;
    }

    public static Optional<byte[]> field(Map<Short, byte[]> c, short tag) {
        return Optional.ofNullable(c.get(tag));
    }

}
