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
package net.ripe.rpki.validator3.storage.encoding;

import net.ripe.rpki.validator3.storage.Bytes;
import net.ripe.rpki.validator3.storage.data.Ref;
import net.ripe.rpki.validator3.storage.data.RpkiObject;
import net.ripe.rpki.validator3.storage.data.RpkiRepository;
import net.ripe.rpki.validator3.storage.data.TrustAnchor;
import net.ripe.rpki.validator3.storage.data.validation.CertificateTreeValidationRun;
import net.ripe.rpki.validator3.storage.data.validation.RrdpRepositoryValidationRun;
import net.ripe.rpki.validator3.storage.data.validation.RsyncRepositoryValidationRun;
import net.ripe.rpki.validator3.storage.data.validation.TrustAnchorValidationRun;
import net.ripe.rpki.validator3.storage.encoding.custom.CustomCoder;
import net.ripe.rpki.validator3.storage.encoding.custom.RefCoder;
import net.ripe.rpki.validator3.storage.encoding.custom.RpkiObjectCoder;
import net.ripe.rpki.validator3.storage.encoding.custom.RpkiRepositoryCoder;
import net.ripe.rpki.validator3.storage.encoding.custom.TrustAnchorCoder;
import net.ripe.rpki.validator3.storage.encoding.custom.validation.CTValidationRunCoder;
import net.ripe.rpki.validator3.storage.encoding.custom.validation.RRValidationRunCoder;
import net.ripe.rpki.validator3.storage.encoding.custom.validation.RSValidationRunCoder;
import net.ripe.rpki.validator3.storage.encoding.custom.validation.TAValidationRunCoder;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

public class CoderFactory {

    public static <T> Coder<T> defaultCoder() {
        return new FSTCoder<>();
    }

    public static <T> Coder<T> defaultCoder(Class<T> c) {
//        return new FSTCoder<>();
//        return new GsonCoder<>(c);
//        return new BsonCoder<>(c);

        final CustomCoder<T> cc = customCoder(c);
        return new Coder<T>() {
            @Override
            public ByteBuffer toBytes(T t) {
                return Bytes.toDirectBuffer(cc.toBytes(t));
            }

            @Override
            public T fromBytes(ByteBuffer bb) {
                return cc.fromBytes(Bytes.toBytes(bb));
            }
        };
    }

    private static Map<Class<?>, CustomCoder<?>> customCoders = registerCustomCoder();

    private static Map<Class<?>, CustomCoder<?>> registerCustomCoder() {
        final Map<Class<?>, CustomCoder<?>> cc = new HashMap<>();
        cc.put(Ref.class, new RefCoder());
        cc.put(RpkiObject.class, new RpkiObjectCoder());
        cc.put(RpkiRepository.class, new RpkiRepositoryCoder());
        cc.put(TrustAnchor.class, new TrustAnchorCoder());
        cc.put(CertificateTreeValidationRun.class, new CTValidationRunCoder());
        cc.put(TrustAnchorValidationRun.class, new TAValidationRunCoder());
        cc.put(RsyncRepositoryValidationRun.class, new RSValidationRunCoder());
        cc.put(RrdpRepositoryValidationRun.class, new RRValidationRunCoder());
        return cc;
    }

    private static <T> CustomCoder<T> customCoder(Class<T> c) {
        CustomCoder<?> coder = customCoders.get(c);
        if (coder == null) {
            throw new IllegalArgumentException("There's no coder for class " + c);
        }
        return (CustomCoder<T>) coder;
    }

}
