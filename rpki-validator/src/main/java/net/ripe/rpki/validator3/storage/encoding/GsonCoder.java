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

import com.google.common.collect.ImmutableList;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import lombok.AllArgsConstructor;
import net.ripe.ipresource.Asn;
import net.ripe.ipresource.IpRange;
import net.ripe.ipresource.IpResourceSet;
import net.ripe.rpki.commons.crypto.CertificateRepositoryObject;
import net.ripe.rpki.validator3.storage.Bytes;
import sun.reflect.generics.reflectiveObjects.ParameterizedTypeImpl;

import javax.security.auth.x500.X500Principal;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.ByteBuffer;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.List;
import java.util.function.Function;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * TODO Either remove it of configure so that it works.
 *
 * @param <T>
 */
public class GsonCoder<T> implements Coder<T> {

    private final Gson gson = getGson();

    public static Gson getGson() {
        return getGson(false);
    }

    public static Gson getPrettyGson() {
        return getGson(true);
    }

    public static Gson getGson(boolean pretty) {
        final GsonBuilder gsonBuilder = new GsonBuilder()
                .registerTypeAdapter(byte[].class, new ByteArraysGsonAdapter())
                .registerTypeAdapter(ImmutableList.class, new ImmutableListAdapter())
                .registerTypeAdapter(IpRange.class, new ParsingAdapter<>(IpRange::parse))
                .registerTypeAdapter(Asn.class, new ParsingAdapter<>(Asn::parse));
        if (pretty) {
            gsonBuilder.setPrettyPrinting();
        }
        return gsonBuilder.create();
    }

    @AllArgsConstructor
    public static class ParsingAdapter<T> implements JsonDeserializer<T>, JsonSerializer<T> {

        private final Function<String, T> parse;

        @Override
        public T deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            return parse.apply(json.getAsJsonPrimitive().getAsString());
        }

        @Override
        public JsonElement serialize(T src, Type typeOfSrc, JsonSerializationContext context) {
            return new JsonPrimitive(src.toString());
        }
    }

    private final Class<T> class_;

    public GsonCoder(Class<T> class_) {
        this.class_ = class_;
    }

    @Override
    public ByteBuffer toBytes(T t) {
        String json = gson.toJson(t);
        return Bytes.toDirectBuffer(json.getBytes(UTF_8));
    }

    @Override
    public T fromBytes(ByteBuffer bb) {
        String json = new String(Bytes.toBytes(bb), UTF_8);
        return gson.fromJson(json, class_);
    }

    static class ByteArraysGsonAdapter implements JsonSerializer<byte[]>, JsonDeserializer<byte[]> {

        @Override
        public byte[] deserialize(JsonElement json, Type type, JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
            return Base64.getDecoder().decode(json.getAsJsonPrimitive().getAsString());
        }

        @Override
        public JsonElement serialize(byte[] bytes, Type type, JsonSerializationContext jsonSerializationContext) {
            return new JsonPrimitive(Base64.getEncoder().encodeToString(bytes));
        }
    }

    static class ImmutableListAdapter implements JsonDeserializer<ImmutableList<?>> {
        @Override
        public ImmutableList<?> deserialize(JsonElement json, Type type, JsonDeserializationContext context) throws JsonParseException {
            final Type type2 = ParameterizedTypeImpl.make(List.class, ((ParameterizedType) type).getActualTypeArguments(), null);
            final List<?> list = context.deserialize(json, type2);
            return ImmutableList.copyOf(list);
        }
    }
}
