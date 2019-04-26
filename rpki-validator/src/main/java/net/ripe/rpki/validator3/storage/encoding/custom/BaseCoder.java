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

import net.ripe.rpki.validator3.storage.data.Base;
import net.ripe.rpki.validator3.storage.data.Key;

import java.util.Map;

public class BaseCoder {

    private final static short ID_TAG = Tags.unique(1);
    private final static short CREATED_AT = Tags.unique(2);
    private final static short UPDATED_AT = Tags.unique(3);

    public static void toBytes(Base base, Encoded encoded) {
        encoded.appendNotNull(ID_TAG, base.key(), Key::getBytes);
        toBytesNoId(base, encoded);
    }

    public static void fromBytes(Map<Short, byte[]> content, Base base) {
        Encoded.field(content, ID_TAG).ifPresent(b -> base.setId(Key.of(b)));
        fromBytesNoId(content, base);
    }

    public static void toBytesNoId(Base base, Encoded encoded) {
        encoded.appendNotNull(CREATED_AT, base.getCreatedAt(), Coders::toBytes);
        encoded.appendNotNull(UPDATED_AT, base.getUpdatedAt(), Coders::toBytes);
    }

    public static void fromBytesNoId(Map<Short, byte[]> content, Base base) {
        Encoded.field(content, CREATED_AT).ifPresent(b -> base.setCreatedAt(Coders.toInstant(b)));
        Encoded.field(content, UPDATED_AT).ifPresent(b -> base.setUpdatedAt(Coders.toInstant(b)));
    }
}
