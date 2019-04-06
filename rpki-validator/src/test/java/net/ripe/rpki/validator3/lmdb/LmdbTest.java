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
package net.ripe.rpki.validator3.lmdb;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.ByteBufferOutput;
import com.esotericsoftware.kryo.io.Input;
import com.fasterxml.uuid.Generators;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.jsoniter.JsonIterator;
import com.jsoniter.output.JsonStream;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import net.ripe.rpki.validator3.storage.Bytes;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.lmdbjava.Dbi;
import org.lmdbjava.Env;
import org.lmdbjava.Txn;
import org.nustaq.serialization.simpleapi.DefaultCoder;
import org.springframework.util.StreamUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.Type;
import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.Random;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;

import static java.nio.ByteBuffer.allocateDirect;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.lmdbjava.DbiFlags.MDB_CREATE;
import static org.lmdbjava.Env.create;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.undercouch.bson4jackson.BsonFactory;


@Ignore
public class LmdbTest {

    private static final String DB_NAME = "test-db";

    @Rule
    public final TemporaryFolder tmp = new TemporaryFolder();

    @Test
    @Ignore
    public void testLmdbSpeedTx() throws IOException {
        final File path = tmp.newFolder();

        final Env<ByteBuffer> env = create()
                .setMapSize(1024 * 1024 * 1024)
                .setMaxDbs(1)
                .open(path);

        final Dbi<ByteBuffer> db = env.openDbi(DB_NAME, MDB_CREATE);

        try (Txn<ByteBuffer> txn = env.txnWrite()) {
            for (long k = 1; k < 10_000_000; k++) {
                final ByteBuffer key = uuidBB();
                db.put(txn, key, bb("blabla_" + key));
            }
            txn.commit();
        }

        env.close();
    }

    @Test
    @Ignore
    public void testLmdbSpeedTxIntKey() throws IOException {
        final File path = tmp.newFolder();

        final Env<ByteBuffer> env = create()
                .setMapSize(1024 * 1024 * 1024)
                .setMaxDbs(1)
                .open(path);

        final Dbi<ByteBuffer> db = env.openDbi(DB_NAME, MDB_CREATE);

        try (Txn<ByteBuffer> txn = env.txnWrite()) {
            for (long k = 1; k < 10_000_000; k++) {
                db.put(txn, bb(k), bb("blabla_" + k));
            }
            txn.commit();
        }

        env.close();
    }

    @Test
    @Ignore
    public void testLmdbSpeedForBinary() throws IOException {
        final Kryo kryo = new Kryo();
        kryo.register(Bla.class);
        kryo.register(byte[].class);
        kryo.register(Thingy.class);

        testTemplate(
                k -> {
                    final ByteBufferOutput bbo = new ByteBufferOutput(1024*1024);
                    Bla bla = generateBla();
                    kryo.writeObject(bbo, bla);
                    byte[] bytes = bbo.toBytes();
                    ByteBuffer bb = allocateDirect(bytes.length);
                    bb.put(bytes).flip();
                    return bb;
                },
                (p, k) -> {
                    ByteBuffer buffer = p.getLeft().get(p.getRight(), k);
                    byte[] data = new byte[buffer.remaining()];
                    buffer.get(data);
                    Bla bla = kryo.readObject(new Input(data), Bla.class);
                    return bla;
                }
        );
    }

    @Test
//    @Ignore
    public void testLmdbSpeedForFastBinary() throws IOException {
        final DefaultCoder coder = new DefaultCoder(true, Bla.class, Thingy.class);
        testTemplate(
                k -> {
                    final Bla bla = generateBla();
                    final byte[] bytes = coder.toByteArray(bla);
                    ByteBuffer bb = allocateDirect(bytes.length);
                    bb.put(bytes).flip();
                    return bb;
                },
                (p, k) -> {
                    ByteBuffer buffer = p.getLeft().get(p.getRight(), k);
                    byte[] data = new byte[buffer.remaining()];
                    buffer.get(data);
                    Bla bla = (Bla) coder.toObject(data);
                    return bla;
                }
        );
    }

    @Test
//    @Ignore
    public void testLmdbSpeedForJson() throws IOException {
        testTemplate(
                k -> {
                    ByteArrayOutputStream bos = new ByteArrayOutputStream(1024);
                    JsonStream.serialize(generateBla(), bos);
                    byte[] bytes = bos.toByteArray();
                    ByteBuffer bb = allocateDirect(bytes.length);
                    bb.put(bytes).flip();
                    return bb;
                },
                (p, k) -> {
                    ByteBuffer buffer = p.getLeft().get(p.getRight(), k);
                    byte[] data = new byte[buffer.remaining()];
                    buffer.get(data);
                    return JsonIterator.deserialize(data).as(Bla.class);
                }
        );
    }

    class ByteArraysAdapter implements JsonSerializer<byte[]>, JsonDeserializer<byte[]> {

        @Override
        public byte[] deserialize(JsonElement json, Type type, JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
            return Base64.getDecoder().decode(json.getAsJsonPrimitive().getAsString());
        }

        @Override
        public JsonElement serialize(byte[] bytes, Type type, JsonSerializationContext jsonSerializationContext) {
            return new JsonPrimitive(Base64.getEncoder().encodeToString(bytes));
        }
    }

    @Test
//    @Ignore
    public void testLmdbSpeedForGson() throws IOException {
        Gson gson = new GsonBuilder()
                .registerTypeAdapter(byte[].class, new ByteArraysAdapter())
                .create();

        testTemplate(
                k -> {
                    String json = gson.toJson(generateBla());
                    return Bytes.toDirectBuffer(json.getBytes(UTF_8));
                },
                (p, k) -> {
                    Dbi<ByteBuffer> db = p.getLeft();
                    Txn<ByteBuffer> tx = p.getRight();
                    ByteBuffer buffer = db.get(tx, k);
                    String json = new String(Bytes.toBytes(buffer), UTF_8);
                    return gson.fromJson(json, Bla.class);
                }
        );
    }

    @Test
//    @Ignore
    public void testLmdbSpeedForBson() throws IOException {
        ObjectMapper mapper = new ObjectMapper(new BsonFactory());
        testTemplate(
                k -> {
                    try {
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        mapper.writeValue(baos, generateBla());
                        return Bytes.toDirectBuffer(baos.toByteArray());
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                },
                (p, k) -> {
                    Dbi<ByteBuffer> db = p.getLeft();
                    Txn<ByteBuffer> tx = p.getRight();
                    ByteBuffer buffer = db.get(tx, k);
                    try {
                        return mapper.readValue(Bytes.toBytes(buffer), Bla.class);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
        );
    }

    @Test
    public void testLmdbSpeedForTwoUUIDs() throws IOException {
        testTemplate(
                k -> uuidBB(),
                (p, k) -> p.getLeft().get(p.getRight(), k)
        );
    }

    @Test
    public void testSizes() throws IOException {
        final DefaultCoder coder = new DefaultCoder(true, Bla.class, Thingy.class);
        final Random r = new Random();
        Bla bla = new Bla(randomBytes(r, 1000), "", 100000, null);
        byte[] bytes = coder.toByteArray(bla);
        System.out.println("bytes.length = " + bytes.length);
    }

    private <T> void testTemplate(Function<ByteBuffer, ByteBuffer> value,
                                  BiFunction<Pair<Dbi<ByteBuffer>, Txn<ByteBuffer>>, ByteBuffer, T> extract) throws IOException {

        long begin = System.currentTimeMillis();
        File path = tmp.newFolder();
        final Env<ByteBuffer> env = create()
                .setMapSize(1024 * 1024 * 1024L)
                .setMaxDbs(1)
                .open(path);

        final Dbi<ByteBuffer> db = env.openDbi(DB_NAME, MDB_CREATE);

        int keyCount = 100_000;
//        int keyCount = 2;
        final ByteBuffer[] keys = new ByteBuffer[keyCount];
        for (int k = 0; k < keyCount; k++) {
            keys[k] = uuidBB();
        }

        try (Txn<ByteBuffer> txn = env.txnWrite()) {
            Stream.of(keys).forEach(k -> db.put(txn, k, value.apply(k)));
            txn.commit();
        }

        try (Txn<ByteBuffer> txn = env.txnRead()) {
            Stream.of(keys)
                    .map(k -> extract.apply(Pair.of(db, txn), k))
                    .forEach(Assert::assertNotNull);
        }

        long end = System.currentTimeMillis();

        System.out.println("time = " + (end - begin) + "ms, env = " + env.info());
        StreamUtils.copy(Runtime.getRuntime().exec("du -sm " + path).getInputStream(), System.out);
        env.close();
    }


    private static ByteBuffer uuidBB() {
        UUID uuid = makeUuid();
        return makeByteBuffer(uuid);
    }

    private static UUID makeUuid() {
        return Generators.timeBasedGenerator().generate();
    }

    private static ByteBuffer makeByteBuffer(UUID uuid) {
        final ByteBuffer key = allocateDirect(16);
        key.putLong(uuid.getMostSignificantBits());
        key.putLong(uuid.getLeastSignificantBits());
        key.flip();
        return key;
    }

    private static <T> ByteBuffer bb(T t) {
        final String s = t.toString();
        final ByteBuffer val = allocateDirect(s.length() * 2);
        val.put(s.getBytes(UTF_8)).flip();
        return val;
    }

    private static ByteBuffer bb(byte[] t) {
        final ByteBuffer val = allocateDirect(t.length);
        val.put(t).flip();
        return val;
    }

    private Bla generateBla() {
        final Random r = new Random();
        Thingy thingy = new Thingy(randomString(r), r.nextInt());
        return new Bla(randomBytes(r), randomString(r), r.nextInt(), thingy);
    }

    private byte[] randomBytes(Random r) {
        return randomBytes(r, r.nextInt(1024));
    }

    private byte[] randomBytes(Random r, int length) {
        byte[] b = new byte[length];
        r.nextBytes(b);
        return b;
    }

    private String randomString(Random r) {
        final int len = r.nextInt(50);
        StringBuilder s = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            s.append(r.nextInt(10));
        }
        return s.toString();
    }

    @Data(staticConstructor = "of")
    @AllArgsConstructor
    @NoArgsConstructor
    static class Bla implements Serializable {
        byte[] stuff;
        String someStringValueLongName;
        int counter;
        Thingy thingy;
    }

    @Data(staticConstructor = "of")
    @AllArgsConstructor
    @NoArgsConstructor
    static class Thingy implements Serializable {
        String thingyContent;
        int alsoThis;
    }
}
