package net.ripe.rpki.validator3.storage.xodus;

        import net.ripe.rpki.validator3.storage.MultiIxMapTest;
        import net.ripe.rpki.validator3.storage.encoding.CoderFactory;
        import org.junit.Before;

public class XodusMultiIxMapTest extends MultiIxMapTest {

    @Before
    public void setUp() throws Exception {
        storage = XodusTests.makeXodus(tmp.newFolder().getAbsolutePath());
        multIxMap = storage.createMultIxMap("test", CoderFactory.makeCoder(String.class));

    }
}
