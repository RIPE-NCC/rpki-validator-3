package net.ripe.rpki.validator3.storage.data.coders;

import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import net.ripe.rpki.validator3.storage.data.Ref;
import net.ripe.rpki.validator3.storage.data.TrustAnchor;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;

@RunWith(JUnitQuickcheck.class)
public class RefCoderTest {

    private final RefCoder<TrustAnchor> coder = new RefCoder<>();

    @Property
    public void formatAndParse(Ref<TrustAnchor> ref) throws Exception {
        assertEquals(ref, coder.fromBytes(coder.toBytes(ref)));
    }

}