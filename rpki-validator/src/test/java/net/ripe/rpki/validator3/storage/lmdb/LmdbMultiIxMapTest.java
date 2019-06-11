package net.ripe.rpki.validator3.storage.lmdb;

import net.ripe.rpki.validator3.storage.MultiIxMapTest;
import net.ripe.rpki.validator3.storage.encoding.CoderFactory;
import org.junit.Before;

public class LmdbMultiIxMapTest extends MultiIxMapTest {

    @Before
    public void setUp() throws Exception {
        storage = LmdbTests.makeLmdb(tmp.newFolder().getAbsolutePath());
        multIxMap = storage.createMultIxMap("test", CoderFactory.makeCoder(String.class));
    }
}
