package net.ripe.rpki.validator3.domain.simple;

import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import net.ripe.rpki.validator3.storage.BinarySerializer;
import net.ripe.rpki.validator3.storage.data.RoaPrefix;
import org.junit.Ignore;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;

@Ignore
@RunWith(JUnitQuickcheck.class)
public class BinarySerializerTest {

    private BinarySerializer bs;

    @Property
    public void formatAndParse(RoaPrefix rp) throws Exception {
        RoaPrefix rp1 = bs.deserialize(bs.serialize(rp), RoaPrefix.class);
        assertEquals(rp, rp1);
    }

}