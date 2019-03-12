package net.ripe.rpki.validator3;

import com.fasterxml.uuid.Generators;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.lmdbjava.Dbi;
import org.lmdbjava.Env;
import org.lmdbjava.Txn;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.UUID;

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

//    @Test
//    public void testLmdbSpeed() throws IOException {
//        final File path = tmp.newFolder();
//
//        System.out.println("path = " + path.getAbsolutePath());
//
//        final Env<ByteBuffer> env = create()
//                .setMapSize(1024 * 1024 * 1024)
//                .setMaxDbs(1)
//                .open(path);
//
//        final Dbi<ByteBuffer> db = env.openDbi(DB_NAME, MDB_CREATE);
//
//        for (long k = 1; k < 100_000; k++) {
//            final ByteBuffer key = uuidBB();
//            db.put(key, bb("blabla_" + key));
//        }
//
//        env.close();
//    }

    @Test
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


    private static ByteBuffer uuidBB() {
        UUID uuid = Generators.timeBasedGenerator().generate();
        final ByteBuffer key = allocateDirect(16);
        key.putLong(uuid.getMostSignificantBits());
        key.putLong(uuid.getLeastSignificantBits());
        key.flip();
        return key;
    }

    private static <T> ByteBuffer bb(T t) {
        final String s = t.toString();
        final ByteBuffer val = allocateDirect(s.length()*2);
        val.put(s.getBytes(UTF_8)).flip();
        return val;
    }
}
