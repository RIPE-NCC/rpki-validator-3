package net.ripe.rpki.validator3.storage.lmdb;

import com.google.common.collect.ImmutableMap;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import net.ripe.rpki.validator3.storage.FSTCoder;
import org.assertj.core.util.Files;
import org.hamcrest.CoreMatchers;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;
import org.lmdbjava.Env;

import javax.validation.constraints.NotNull;
import java.io.File;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Optional;

import static net.ripe.rpki.validator3.storage.lmdb.StoreTest.intKey;
import static org.assertj.core.api.Assertions.not;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.number.OrderingComparison.greaterThan;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeThat;
import static org.lmdbjava.Env.create;

@RunWith(JUnitQuickcheck.class)
public class StorePropTest {

    private static Env<ByteBuffer> env;
    private static Store<String> store;

    private static File lmdbDir;

    @BeforeClass
    public static void setUp() throws Exception {
        lmdbDir = Files.temporaryFolder();

        env = create()
                .setMapSize(1024 * 1024 * 1024L)
                .setMaxDbs(100)
                .open(lmdbDir);

        store = new Store<>(env, "test", new FSTCoder<>(), ImmutableMap.of(LENGTH_INDEX, s -> intKey(s.length())));
    }


    private static final String LENGTH_INDEX = "length-index";


    @Property
    public void storedIsThere(String key, String value) throws Exception {
        assumeThat(key, CoreMatchers.not(equalTo(null)));
        assumeThat(key, CoreMatchers.not(equalTo("")));
        assumeThat(value, CoreMatchers.not(equalTo(null)));

        Key k = StoreTest.key(key);
        Optional<String> oldValue = store.put(k, value);
        try (Tx.Read<ByteBuffer> tx = Tx.read(env)) {
            assertEquals(value, store.get(tx, k).get());
            List<String> byIndex = store.getByIndex(LENGTH_INDEX, tx.txn(), intKey(value.length()));
            assertTrue(byIndex.stream().anyMatch(s -> s.equals(value)));
            oldValue.ifPresent(s1 -> {
                if (!s1.equals(value)) {
                    assertNotEquals(s1, store.get(tx, k).get());
                    List<String> oldByIndex = store.getByIndex(LENGTH_INDEX, tx.txn(), intKey(s1.length()));
                    assertFalse(oldByIndex.stream().anyMatch(s -> s.equals(s1)));
                }
            });
        }
    }
}
