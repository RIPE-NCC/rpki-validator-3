package net.ripe.rpki.validator3.storage.encoding;

import com.google.common.collect.ImmutableList;
import net.ripe.rpki.validator3.storage.data.Base;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class CoderTest {

    private static <T> List<Coder<T>> makeCoders(T t) {
        return ImmutableList.of(
                new FSTCoder<>(),
                new BsonCoder<>((Class<T>) t.getClass()),
                new GsonCoder<>((Class<T>) t.getClass()));
    }

    private final List<Base<?>> data = ImmutableList.of(
//            new RpkiObject(),
//            new RrdpRepositoryValidationRun(),
//            new RsyncRepositoryValidationRun(),
    );

    public void testAll() {
        data.forEach(b ->
                makeCoders(b).forEach(c -> {
            Base<?> b1 = c.fromBytes(c.toBytes(b));
            assertEquals(b, b1);
        }));
    }

}