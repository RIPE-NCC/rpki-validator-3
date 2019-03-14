package net.ripe.rpki.validator3.lmdb;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.ByteBufferOutput;
import com.esotericsoftware.kryo.io.Input;
import com.fasterxml.uuid.Generators;
import com.jsoniter.JsonIterator;
import com.jsoniter.output.JsonStream;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.util.Random;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;

import static java.nio.ByteBuffer.allocateDirect;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.lmdbjava.DbiFlags.MDB_CREATE;
import static org.lmdbjava.Env.create;


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
    public void testLmdTwoDbTx() throws IOException {
        final File path = tmp.newFolder();
        final Env<ByteBuffer> env = create()
                .setMapSize(1024 * 1024 * 1024)
                .setMaxDbs(2)
                .open(path);

        final Dbi<ByteBuffer> db1 = env.openDbi(DB_NAME, MDB_CREATE);
        final Dbi<ByteBuffer> db2 = env.openDbi(DB_NAME + "1", MDB_CREATE);

        final ByteBuffer k1 = uuidBB();
        final ByteBuffer k2 = uuidBB();

        try (Txn<ByteBuffer> txn = env.txnWrite()) {
            db1.put(txn, k1, bb("v1"));
            db2.put(txn, k1, bb("v2"));
            txn.abort();
        }

        try (Txn<ByteBuffer> txn = env.txnWrite()) {
            db1.put(txn, k2, bb("v1"));
            db2.put(txn, k2, bb("v2"));
            txn.commit();
        }

        try (Txn<ByteBuffer> readTxn = env.txnRead()) {
            assertNull(db1.get(readTxn, k1));
            assertNull(db2.get(readTxn, k1));

            assertNotNull(db1.get(readTxn, k2));
            assertNotNull(db2.get(readTxn, k2));
        }

        env.close();
    }

    @Test
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
//                    System.out.println("bla1 = " + bla);
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
//                    System.out.println("bla2 = " + bla);
                    return bla;
                }
        );
    }

    @Test
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
//                    System.out.println("bla3 = " + bla);
                    return bla;
                }
        );
    }

    @Test
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

    private <T> void testTemplate(Function<ByteBuffer, ByteBuffer> value,
                                  BiFunction<Pair<Dbi<ByteBuffer>, Txn<ByteBuffer>>, ByteBuffer, T> extract) throws IOException {
        File path = tmp.newFolder();
        final Env<ByteBuffer> env = create()
                .setMapSize(8 * 1024 * 1024 * 1024L)
                .setMaxDbs(1)
                .open(path);

        final Dbi<ByteBuffer> db = env.openDbi(DB_NAME, MDB_CREATE);

        int keyCount = 20_000;
//        int keyCount = 2;
        final ByteBuffer[] keys = new ByteBuffer[keyCount];
        for (int k = 0; k < keyCount; k++) {
            keys[k] = uuidBB();
        }

        try (Txn<ByteBuffer> txn = env.txnWrite()) {
            Stream.of(keys).forEach(k -> {
                db.put(txn, k, value.apply(k));
            });
            txn.commit();
        }

        try (Txn<ByteBuffer> txn = env.txnRead()) {
            Stream.of(keys)
                    .map(k -> extract.apply(Pair.of(db, txn), k))
                    .forEach(Assert::assertNotNull);
        }

        System.out.println("env = " + env.info());
        StreamUtils.copy(Runtime.getRuntime().exec("du -sm " + path).getInputStream(), System.out);
        env.close();
    }


    @Test
    public void testWrite() {
        File path = new File("/tmp/test-lmdb-ser1");

        final DefaultCoder coder = new DefaultCoder(true, Bla.class, Thingy.class);

        final Env<ByteBuffer> env = create()
                .setMapSize(8 * 1024 * 1024 * 1024L)
                .setMaxDbs(1)
                .open(path);

        final Dbi<ByteBuffer> db = env.openDbi(DB_NAME, MDB_CREATE);

        Bla bla = generateBla();
        try (Txn<ByteBuffer> txn = env.txnWrite()) {
            db.put(txn, bb(2), bb(coder.toByteArray(bla)));
            txn.commit();
        }
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
        final int len = r.nextInt(64*1024);
        byte[] b = new byte[len];
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
