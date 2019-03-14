package net.ripe.rpki.validator3.storage.lmdb;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import net.ripe.rpki.validator3.storage.Bytes;
import net.ripe.rpki.validator3.storage.FSTSerializer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.lmdbjava.Env;
import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.lmdbjava.Env.create;

public class StoreTest {

    @Rule
    public final TemporaryFolder tmp = new TemporaryFolder();

    private Env<ByteBuffer> env;
    private Store<String> store;

    private static final String LENGTH_INDEX = "length-index";

    @Before
    public void setUp() throws Exception {
        env = create()
                .setMapSize(1024 * 1024 * 1024L)
                .setMaxDbs(100)
                .open(tmp.newFolder());

        store = new Store<>(env, "test", new FSTSerializer<>(), ImmutableMap.of(LENGTH_INDEX, StoreTest::stringLen));
    }

    @Test
    public void putAndGetByIndex() {
        putAndGet("a");
        putAndGet("aa");
        putAndGet("ab");
        putAndGet("bbb");
        putAndGet("xxx");

        try (Txn<ByteBuffer> txn = env.txnRead()) {
            assertEquals(Sets.newHashSet("a"), new HashSet<>(store.getByIndex(LENGTH_INDEX, txn, intKey(1))));
            assertEquals(Sets.newHashSet("aa", "ab"), new HashSet<>(store.getByIndex(LENGTH_INDEX, txn, intKey(2))));
            assertEquals(Sets.newHashSet("bbb", "xxx"), new HashSet<>(store.getByIndex(LENGTH_INDEX, txn, intKey(3))));
        }
    }

    @Test(expected = NullPointerException.class)
    public void putAndGetNull() {
        putAndGet(null);
    }

    @Test(expected = NullPointerException.class)
    public void putAndGetNullKey() {
        store.put(null, "x");
    }

    private void putAndGet(String v) {
        final Key key = key(UUID.randomUUID());
        store.put(key, v);
        try (Txn<ByteBuffer> txn = env.txnRead()) {
            assertEquals(v, store.get(txn, key).get());
        }
    }

    private static Key stringLen(String s) {
        int length = s.length();
        return intKey(length);
    }

    private static Key intKey(int length) {
        final ByteBuffer bb = ByteBuffer.allocateDirect(Integer.BYTES);
        bb.putInt(length).flip();
        return new Key(bb);
    }

    private static Key key(Object o) {
        return new Key(Bytes.toDirectBuffer(o.toString().getBytes()));
    }

}