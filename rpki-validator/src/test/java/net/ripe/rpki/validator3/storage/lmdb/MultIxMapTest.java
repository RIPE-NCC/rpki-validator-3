package net.ripe.rpki.validator3.storage.lmdb;

import com.google.common.collect.Sets;
import net.ripe.rpki.validator3.storage.FSTCoder;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.lmdbjava.Env;

import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.UUID;

import static net.ripe.rpki.validator3.storage.lmdb.IxMapTest.key;
import static org.junit.Assert.assertEquals;
import static org.lmdbjava.Env.create;

public class MultIxMapTest {

    @Rule
    public final TemporaryFolder tmp = new TemporaryFolder();

    private Env<ByteBuffer> env;
    private MultIxMap<String> ixMap;

    private static final String LENGTH_INDEX = "length-index";

    @Before
    public void setUp() throws Exception {
        env = create()
                .setMapSize(1024 * 1024 * 1024L)
                .setMaxDbs(100)
                .open(tmp.newFolder());

        ixMap = new MultIxMap<>(env, "test", new FSTCoder<>());
    }

    @Test
    public void putAndGetByIndex() {
        final Key k1 = key(UUID.randomUUID());
        final Key k2 = key(UUID.randomUUID());
        final Key k3 = key(UUID.randomUUID());
        ixMap.put(k1, "a");

        try (Tx.Read<ByteBuffer> tx = Tx.read(env)) {
            assertEquals(Sets.newHashSet("a"), new HashSet<>(ixMap.get(tx, k1)));
            assertEquals(Sets.newHashSet("a"), new HashSet<>(ixMap.values(tx)));
        }

        ixMap.put(k1, "b");

        try (Tx.Read<ByteBuffer> tx = Tx.read(env)) {
            assertEquals(Sets.newHashSet("a", "b"), new HashSet<>(ixMap.get(tx, k1)));
            assertEquals(Sets.newHashSet("a", "b"), new HashSet<>(ixMap.values(tx)));
        }

        ixMap.put(k2, "aa");
        ixMap.put(k2, "bbb");
        ixMap.put(k2, "xxx");

        try (Tx.Read<ByteBuffer> tx = Tx.read(env)) {
            assertEquals(Sets.newHashSet("a", "b"), new HashSet<>(ixMap.get(tx, k1)));
            assertEquals(Sets.newHashSet("aa", "bbb", "xxx"), new HashSet<>(ixMap.get(tx, k2)));
            assertEquals(Sets.newHashSet("a", "b", "aa", "bbb", "xxx"), new HashSet<>(ixMap.values(tx)));
        }
    }

}