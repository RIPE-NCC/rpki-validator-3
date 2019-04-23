package net.ripe.rpki.validator3.storage.data.coders;

import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import org.junit.runner.RunWith;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNotNull;

@RunWith(JUnitQuickcheck.class)
public class EncodedTest {

    @Property
    public void encodeAndDecode(List<String> s) {
        if (s != null && s.size() < Short.MAX_VALUE) {
            final Encoded e = new Encoded();
            for (short tag = 0; tag < s.size(); tag++) {
                e.append(tag, s.get(tag).getBytes(StandardCharsets.UTF_8));
            }
            final Encoded encoded = Encoded.fromByteArray(e.toByteArray());
            for (Map.Entry<Short, byte[]> entry : e.getContent().entrySet()) {
                byte[] bytes = encoded.getContent().get(entry.getKey());
                assertNotNull("bytes for " + entry.getKey(), bytes);
                assertArrayEquals(entry.getValue(), bytes);
            }
        }
    }
}