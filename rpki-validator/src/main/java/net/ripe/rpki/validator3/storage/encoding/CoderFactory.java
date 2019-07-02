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

import com.google.common.primitives.Longs;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.validator3.storage.data.Key;
import net.ripe.rpki.validator3.storage.data.Ref;
import net.ripe.rpki.validator3.storage.data.RpkiObject;
import net.ripe.rpki.validator3.storage.data.RpkiRepository;
import net.ripe.rpki.validator3.storage.data.TrustAnchor;
import net.ripe.rpki.validator3.storage.data.validation.CertificateTreeValidationRun;
import net.ripe.rpki.validator3.storage.data.validation.RrdpRepositoryValidationRun;
import net.ripe.rpki.validator3.storage.data.validation.RsyncRepositoryValidationRun;
import net.ripe.rpki.validator3.storage.data.validation.TrustAnchorValidationRun;
import net.ripe.rpki.validator3.storage.encoding.custom.RefCoder;
import net.ripe.rpki.validator3.storage.encoding.custom.RpkiObjectCoder;
import net.ripe.rpki.validator3.storage.encoding.custom.RpkiRepositoryCoder;
import net.ripe.rpki.validator3.storage.encoding.custom.TrustAnchorCoder;
import net.ripe.rpki.validator3.storage.encoding.custom.validation.CTValidationRunCoder;
import net.ripe.rpki.validator3.storage.encoding.custom.validation.RRValidationRunCoder;
import net.ripe.rpki.validator3.storage.encoding.custom.validation.RSValidationRunCoder;
import net.ripe.rpki.validator3.storage.encoding.custom.validation.TAValidationRunCoder;

import java.util.HashMap;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;

@Slf4j
public class CoderFactory {

    public static <T> Coder<T> makeCoder(Class<T> c) {
        final Coder<T> cc = Coder(c);
        if (cc == null) {
            final GsonCoder<T> gsonCoder = new GsonCoder<>(c);
            log.warn("There's no custom coder for the type {}, using a {}", c, gsonCoder.getClass());
            return gsonCoder;
        }
        return new Coder<T>() {
            @Override
            public byte[] toBytes(T t) {
                return cc.toBytes(t);
            }

            @Override
            public T fromBytes(byte[] bb) {
                return cc.fromBytes(bb);
            }
        };
    }

    private static Map<Class<?>, Coder<?>> customCoders = registerCustomCoder();

    private static Map<Class<?>, Coder<?>> registerCustomCoder() {
        final Map<Class<?>, Coder<?>> cc = new HashMap<>();
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

    private static <T> Coder<T> Coder(Class<T> c) {
        return (Coder<T>) customCoders.get(c);
    }

    public static Coder<Key> keyCoder() {
        return new Coder<Key>() {
            @Override
            public byte[] toBytes(Key key) {
                return key.getBytes();
            }

            @Override
            public Key fromBytes(byte[] bytes) {
                return Key.of(bytes);
            }
        };
    }

    public static Coder<Long> longCoder() {
        return new Coder<Long>() {
            @Override
            public byte[] toBytes(Long z) {
                return Longs.toByteArray(z);
            }

            @Override
            public Long fromBytes(byte[] bb) {
                return Longs.fromByteArray(bb);
            }
        };
    }

    public static Coder<String> stringCoder() {
        return new Coder<String>() {
            @Override
            public byte[] toBytes(String z) {
                return z.getBytes(UTF_8);
            }

            @Override
            public String fromBytes(byte[] bb) {
                return new String(bb, UTF_8);
            }
        };
    }

}
