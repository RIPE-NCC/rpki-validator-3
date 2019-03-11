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
import java.util.concurrent.CountDownLatch;

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
    public void testLmdbSpeed() throws IOException {
        final File path = tmp.newFolder();

        System.out.println("path = " + path.getAbsolutePath());

        final Env<ByteBuffer> env = create()
                .setMapSize(1024 * 1024 * 1024)
                .setMaxDbs(1)
                .open(path);

        final Dbi<ByteBuffer> db = env.openDbi(DB_NAME, MDB_CREATE);

        for (long k = 1; k < 100_000; k++) {
            final ByteBuffer key = makeKey(env);
            final ByteBuffer val = allocateDirect(700);
            val.put(("Blabla_" + k).getBytes(UTF_8)).flip();
            db.put(key, val);
        }

        env.close();
    }

    @Test
    public void testLmdbSpeedTx() throws IOException {
        final File path = tmp.newFolder();

        System.out.println("path = " + path.getAbsolutePath());

        final Env<ByteBuffer> env = create()
                .setMapSize(1024 * 1024 * 1024)
                .setMaxDbs(1)
                .open(path);

        final Dbi<ByteBuffer> db = env.openDbi(DB_NAME, MDB_CREATE);

        try (Txn<ByteBuffer> txn = env.txnWrite()) {
            for (long k = 1; k < 100_000; k++) {
                final ByteBuffer key = makeKey(env);
                final ByteBuffer val = allocateDirect(700);
                val.put(("Blabla_" + k).getBytes(UTF_8)).flip();
                db.put(txn, key, val);
            }
            txn.commit();
        }

        env.close();
    }

    @Test
    public void testLmdTwoDbTx() throws IOException {
        final File path = tmp.newFolder();

        System.out.println("path = " + path.getAbsolutePath());

        final Env<ByteBuffer> env = create()
                .setMapSize(1024 * 1024 * 1024)
                .setMaxDbs(2)
                .open(path);

        final Dbi<ByteBuffer> db1 = env.openDbi(DB_NAME, MDB_CREATE);
        final Dbi<ByteBuffer> db2 = env.openDbi(DB_NAME + "1", MDB_CREATE);

        Txn<ByteBuffer> readTxn = env.txnRead();

        final ByteBuffer k1 = makeKey(env);
        try (Txn<ByteBuffer> txn = env.txnWrite()) {
            db1.put(txn, k1, makeVal("v1"));
//            db2.put(txn, k1, makeVal("v2"));
            txn.abort();
//            txn.close();
        }

        final ByteBuffer k2 = makeKey(env);
        try (Txn<ByteBuffer> txn1 = env.txnWrite()) {
            db1.put(txn1, k2, makeVal("v1"));
//            db2.put(txn, k2, makeVal("v2"));
            txn1.commit();
        }

        assertNull(db1.get(readTxn, k1));
        assertNull(db2.get(readTxn, k1));

        assertNotNull(db1.get(readTxn, k2));
        assertNotNull(db2.get(readTxn, k2));

        env.close();
    }

    private static ByteBuffer makeKey(Env<ByteBuffer> env) {
        UUID uuid = Generators.timeBasedGenerator().generate();
        final ByteBuffer key = allocateDirect(env.getMaxKeySize());
        key.putLong(uuid.getMostSignificantBits());
        key.putLong(uuid.getLeastSignificantBits());
        return key;
    }

    private static ByteBuffer makeVal(String s) {
        final ByteBuffer val = allocateDirect(s.length()*2);
        val.put(s.getBytes(UTF_8)).flip();
        return val;
    }
}
