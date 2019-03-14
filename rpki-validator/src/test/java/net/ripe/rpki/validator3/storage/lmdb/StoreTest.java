package net.ripe.rpki.validator3.storage.lmdb;

import com.google.common.collect.ImmutableMap;
import com.jsoniter.spi.OmitValue;
import com.pholser.junit.quickcheck.Property;
import net.ripe.rpki.validator3.storage.Bytes;
import net.ripe.rpki.validator3.storage.FSTSerializer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.lmdbjava.Env;
import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.lmdbjava.Env.create;

public class StoreTest {

    @Rule
    public final TemporaryFolder tmp = new TemporaryFolder();

    private Env<ByteBuffer> env;
    private Store<String> store;

    @Before
    public void setUp() throws Exception {
        env = create()
                .setMapSize(1024 * 1024 * 1024L)
                .setMaxDbs(100)
                .open(tmp.newFolder());

        store = new Store<>(env, "test", new FSTSerializer<>(), ImmutableMap.of("length-index", StoreTest::stringLen));
    }

    private static Key stringLen(String s) {
        final ByteBuffer bb = ByteBuffer.allocateDirect(Integer.BYTES);
        bb.putInt(s.length()).flip();
        return new Key(bb);
    }

    private static Key key(Object o) {
        return new Key(Bytes.toDirectBuffer(o.toString().getBytes()));
    }

    @Test
    public void putAndGet() throws Exception {
        putAndGet("v");
        putAndGet("v1");
        putAndGet("v2");
    }

    @Test(expected = NullPointerException.class)
    public void putAndGetNull() throws Exception {
        putAndGet(null);
    }

    @Test(expected = NullPointerException.class)
    public void putAndGetNullKey() throws Exception {
        store.put(null, "x");
    }

    private void putAndGet(String v) {
        final Key k1 = key(UUID.randomUUID());
        store.put(k1, v);
        try (Txn<ByteBuffer> txn = env.txnRead()) {
            assertEquals(v, store.get(txn, k1).get());
        }
    }

    @Property
    public void putAndGetByIndex(byte[] bytes) throws Exception {
    }


}