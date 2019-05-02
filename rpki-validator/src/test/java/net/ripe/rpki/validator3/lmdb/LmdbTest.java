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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import de.undercouch.bson4jackson.BsonFactory;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import net.ripe.rpki.validator3.storage.Bytes;
import net.ripe.rpki.validator3.storage.encoding.custom.CustomCoder;
import net.ripe.rpki.validator3.storage.encoding.custom.Coders;
import net.ripe.rpki.validator3.storage.encoding.custom.Encoded;
import net.ripe.rpki.validator3.storage.encoding.custom.Tags;
import net.ripe.rpki.validator3.util.Time;
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
import org.nustaq.serialization.simpleapi.MinBinCoder;
import org.springframework.util.StreamUtils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.Type;
import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.nio.ByteBuffer.allocateDirect;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.lmdbjava.DbiFlags.MDB_CREATE;
import static org.lmdbjava.Env.create;


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
                buffer -> {
                    byte[] data = new byte[buffer.remaining()];
                    buffer.get(data);
                    Bla bla = (Bla) coder.toObject(data);
                    return bla;
                }
        );
    }

    @Test
//    @Ignore
    public void testLmdbSpeedForMinBin() throws IOException {
        final DefaultCoder coder = new MinBinCoder(true, Bla.class, Thingy.class);
        testTemplate(
                k -> {
                    final Bla bla = generateBla();
                    final byte[] bytes = coder.toByteArray(bla);
                    ByteBuffer bb = allocateDirect(bytes.length);
                    bb.put(bytes).flip();
                    return bb;
                },
                buffer -> {
                    byte[] data = new byte[buffer.remaining()];
                    buffer.get(data);
                    Bla bla = (Bla) coder.toObject(data);
                    return bla;
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
                buffer -> {
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
                buffer -> {
                    try {
                        return mapper.readValue(Bytes.toBytes(buffer), Bla.class);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
        );
    }


    @Test
//    @Ignore
    public void testLmdbSpeedForZZZCustom() throws IOException {
        BlaCoder blaCoder = new BlaCoder();
        testTemplate(
                k -> {
                    try {
                        return Bytes.toDirectBuffer(blaCoder.toBytes(generateBla()));
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                },
                buffer -> blaCoder.fromBytes(Bytes.toBytes(buffer))
        );
    }


    @Test
//    @Ignore
    public void testBlaCoder() throws IOException {
        BlaCoder blaCoder = new BlaCoder();
        Bla bla = generateBla();
        Bla bla1 = blaCoder.fromBytes(blaCoder.toBytes(bla));
        assertEquals(bla, bla1);
    }

//
//    @Test
//    public void testLmdbSpeedForTwoUUIDs() throws IOException {
//        testTemplate(
//                k -> uuidBB(),
//                (p, k) -> p.getLeft().get(p.getRight(), k)
//        );
//    }

    @Test
    public void testSizes() throws IOException {
        final DefaultCoder coder = new DefaultCoder(true, Bla.class, Thingy.class);
        final Random r = new Random();
        Bla bla = new Bla(randomBytes(r, 1000), "", 100000, null);
        byte[] bytes = coder.toByteArray(bla);
        System.out.println("bytes.length = " + bytes.length);
    }

    private <T> void testTemplate(Function<ByteBuffer, ByteBuffer> value,
                                  Function<ByteBuffer, T> extract) throws IOException {

        long begin = System.currentTimeMillis();
        File path = tmp.newFolder();
        final Env<ByteBuffer> env = create()
                .setMapSize(1024 * 1024 * 1024L)
                .setMaxDbs(1)
                .open(path);

        final Dbi<ByteBuffer> db = env.openDbi(DB_NAME, MDB_CREATE);

        int keyCount = 50_000;
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
                    .map(k -> {
                        ByteBuffer bb = db.get(txn, k);
                        return extract.apply(bb);
                    })
                    .forEach(Assert::assertNotNull);
        }

        long end = System.currentTimeMillis();

        Pair<List<ByteBuffer>, Long> stime = Time.timed(() -> Stream.of(keys).map(value).collect(Collectors.toList()));
        Pair<List<T>, Long> dtime = Time.timed(() -> stime.getLeft().stream().map(extract).collect(Collectors.toList()));

        System.out.println("total time = " + (end - begin) + "ms, " +
                "stime = " + stime.getRight() + "ms, " +
                "dtime = " + dtime.getRight() + "ms, env = " + env.info());
        StreamUtils.copy(Runtime.getRuntime().exec("du -sm " + path).getInputStream(), System.out);
        env.close();
    }


    private static ByteBuffer uuidBB() {
        UUID uuid = makeUuid();
        return makeByteBuffer(uuid);
    }

    private static UUID makeUuid() {
        return UUID.randomUUID();
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
        return randomBytes(r, r.nextInt(3000));
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

    @EqualsAndHashCode(callSuper = true)
    @AllArgsConstructor
    @NoArgsConstructor
    static class DerivedBla extends Bla {
        String extra;
    }

    @Data(staticConstructor = "of")
    @AllArgsConstructor
    @NoArgsConstructor
    static class Thingy implements Serializable {
        String thingyContent;
        int alsoThis;
    }

    static class BlaCoder implements CustomCoder<Bla> {

        private static final Tags tags = new Tags();
        private final static short STUFF = tags.unique(1);
        private final static short LONG_STRING = tags.unique(2);
        private final static short COUNTER = tags.unique(3);
        private final static short THINGY = tags.unique(4);

        private final static ThingyCoder thingyCoder = new ThingyCoder();

        @Override
        public byte[] toBytes(Bla bla) {
            Encoded encoded = new Encoded();
            encoded.appendNotNull(LONG_STRING, bla.getSomeStringValueLongName(), Coders::toBytes);
            encoded.appendNotNull(STUFF, bla.stuff, b -> b);
            encoded.appendNotNull(COUNTER, bla.getCounter(), Coders::toBytes);
            encoded.appendNotNull(THINGY, bla.getThingy(), thingyCoder::toBytes);
            return encoded.toByteArray();
        }

        @Override
        public Bla fromBytes(byte[] bytes) {
            Map<Short, byte[]> content = Encoded.fromByteArray(bytes).getContent();
            final Bla bla = new Bla();
            Encoded.field(content, LONG_STRING).ifPresent(b -> bla.setSomeStringValueLongName(Coders.toString(b)));
            Encoded.field(content, STUFF).ifPresent(bla::setStuff);
            Encoded.field(content, COUNTER).ifPresent(b -> bla.setCounter(Coders.toInt(b)));
            Encoded.field(content, THINGY).ifPresent(b -> bla.setThingy(thingyCoder.fromBytes(b)));
            return bla;
        }
    }

    static class ThingyCoder implements CustomCoder<Thingy> {

        private static final Tags tags = new Tags();
        private final static short ALSO_THIS = tags.unique(1);
        private final static short THINGY_CONTENT = tags.unique(2);

        @Override
        public byte[] toBytes(Thingy t) {
            Encoded encoded = new Encoded();
            encoded.appendNotNull(ALSO_THIS, t.getAlsoThis(), Coders::toBytes);
            encoded.appendNotNull(THINGY_CONTENT, t.getThingyContent(), Coders::toBytes);
            return encoded.toByteArray();
        }

        @Override
        public Thingy fromBytes(byte[] bytes) {
            Map<Short, byte[]> content = Encoded.fromByteArray(bytes).getContent();
            final Thingy t = new Thingy();
            Encoded.field(content, ALSO_THIS).ifPresent(b -> t.setAlsoThis(Coders.toInt(b)));
            Encoded.field(content, THINGY_CONTENT).ifPresent(b -> t.setThingyContent(Coders.toString(b)));
            return t;
        }
    }
}
