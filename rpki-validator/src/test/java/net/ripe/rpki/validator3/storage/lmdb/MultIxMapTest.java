package net.ripe.rpki.validator3.storage.lmdb;

import com.google.common.collect.Sets;
import net.ripe.rpki.validator3.storage.FSTCoder;
import net.ripe.rpki.validator3.storage.Lmdb;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.HashSet;
import java.util.UUID;

import static net.ripe.rpki.validator3.storage.lmdb.IxMapTest.key;
import static org.junit.Assert.assertEquals;

public class MultIxMapTest {

    @Rule
    public final TemporaryFolder tmp = new TemporaryFolder();

    private Lmdb lmdb;
    private MultIxMap<String> multIxMap;

    @Before
    public void setUp() throws Exception {
        lmdb = LmdbTests.makeLmdb(tmp.newFolder().getAbsolutePath());
        multIxMap = new MultIxMap<>(lmdb.getEnv(), "test", new FSTCoder<>());
    }

    @Test
    public void putAndGetBack() {
        final Key k1 = key(UUID.randomUUID());
        final Key k2 = key(UUID.randomUUID());
        final Key k3 = key(UUID.randomUUID());
        multIxMap.put(k1, "a");

        try (Tx.Read tx = lmdb.readTx()) {
            assertEquals(Sets.newHashSet("a"), new HashSet<>(multIxMap.get(tx, k1)));
            assertEquals(Sets.newHashSet("a"), new HashSet<>(multIxMap.values(tx)));
        }

        multIxMap.put(k1, "b");

        try (Tx.Read tx = lmdb.readTx()) {
            assertEquals(Sets.newHashSet("a", "b"), new HashSet<>(multIxMap.get(tx, k1)));
            assertEquals(Sets.newHashSet("a", "b"), new HashSet<>(multIxMap.values(tx)));
        }

        multIxMap.put(k2, "aa");
        multIxMap.put(k2, "bbb");
        multIxMap.put(k2, "xxx");

        try (Tx.Read tx = lmdb.readTx()) {
            assertEquals(Sets.newHashSet("a", "b"), new HashSet<>(multIxMap.get(tx, k1)));
            assertEquals(Sets.newHashSet("aa", "bbb", "xxx"), new HashSet<>(multIxMap.get(tx, k2)));
            assertEquals(Sets.newHashSet("a", "b", "aa", "bbb", "xxx"), new HashSet<>(multIxMap.values(tx)));
        }
    }

    @Test
    public void putAndDelete() {
        final Key k1 = key(UUID.randomUUID());
        final Key k2 = key(UUID.randomUUID());
        final Key k3 = key(UUID.randomUUID());
        multIxMap.put(k1, "a");

        try (Tx.Read tx = lmdb.readTx()) {
            assertEquals(Sets.newHashSet("a"), new HashSet<>(multIxMap.get(tx, k1)));
            assertEquals(Sets.newHashSet("a"), new HashSet<>(multIxMap.values(tx)));
        }

        multIxMap.delete(k1);
        try (Tx.Read tx = lmdb.readTx()) {
            assertEquals(Collections.emptySet(), new HashSet<>(multIxMap.get(tx, k1)));
            assertEquals(Collections.emptySet(), new HashSet<>(multIxMap.values(tx)));
        }

        multIxMap.put(k1, "a");
        multIxMap.put(k1, "b");

        multIxMap.delete(k1, "b");
        try (Tx.Read tx = lmdb.readTx()) {
            assertEquals(Sets.newHashSet("a"), new HashSet<>(multIxMap.get(tx, k1)));
            assertEquals(Sets.newHashSet("a"), new HashSet<>(multIxMap.values(tx)));
        }

        multIxMap.put(k1, "a");
        multIxMap.put(k1, "b");
        multIxMap.put(k2, "aa");
        multIxMap.put(k2, "bb");

        multIxMap.delete(k1, "b");
        try (Tx.Read tx = lmdb.readTx()) {
            assertEquals(Sets.newHashSet("a"), new HashSet<>(multIxMap.get(tx, k1)));
            assertEquals(Sets.newHashSet("a", "aa", "bb"), new HashSet<>(multIxMap.values(tx)));
        }
    }

}