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

import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.validator3.storage.Bytes;
import org.nustaq.serialization.FSTBasicObjectSerializer;
import org.nustaq.serialization.FSTClazzInfo;
import org.nustaq.serialization.FSTObjectInput;
import org.nustaq.serialization.FSTObjectOutput;
import org.nustaq.serialization.simpleapi.DefaultCoder;
import org.nustaq.serialization.simpleapi.MinBinCoder;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;

@Component
@Slf4j
public class FSTCoder<T> implements Coder<T> {

    private ThreadLocal<DefaultCoder> coder;

    public FSTCoder() {
        final Class[] registered = getRegisteredClasses();
        coder = ThreadLocal.withInitial(() -> {
            MinBinCoder minBinCoder = new MinBinCoder(true, registered);
            minBinCoder.getConf().registerSerializer(Instant.class, new InstantSerializer(), false);
            return minBinCoder;
        });
    }

    private static Class[] registeredClasses = null;

    private static synchronized Class[] getRegisteredClasses() {
        if (registeredClasses == null) {
//            final Reflections reflections = new Reflections("net.ripe.rpki.validator3.storage.data");
//            final Set<Class<?>> annotated = reflections.getTypesAnnotatedWith(Binary.class);
//            registeredClasses = annotated.toArray(new Class[0]);
            registeredClasses = new Class[0];
            Arrays.sort(registeredClasses, Comparator.comparing(Class::getName));
        }
        return registeredClasses;
    }

    @Override
    public ByteBuffer toBytes(T t) {
        return Bytes.toDirectBuffer(coder.get().toByteArray(t));
    }

    @Override
    @SuppressWarnings("unchecked")
    public T fromBytes(ByteBuffer bb) {
        return (T) coder.get().toObject(Bytes.toBytes(bb));
    }

    private class InstantSerializer extends FSTBasicObjectSerializer {
        @Override
        public void writeObject(FSTObjectOutput out, Object toWrite, FSTClazzInfo clzInfo, FSTClazzInfo.FSTFieldInfo referencedBy, int streamPosition) throws IOException {
            out.writeLong(((Instant)toWrite).toEpochMilli());
        }

        @Override
        public Object instantiate(Class objectClass, FSTObjectInput in, FSTClazzInfo serializationInfo, FSTClazzInfo.FSTFieldInfo referencee, int streamPosition) throws Exception {
            Instant instant = Instant.ofEpochMilli(in.readLong());
            in.registerObject(instant, streamPosition, serializationInfo, referencee);
            return instant;
        }
    }
}
