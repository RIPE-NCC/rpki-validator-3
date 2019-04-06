package net.ripe.rpki.validator3.storage.encoding;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.undercouch.bson4jackson.BsonFactory;
import lombok.Data;
import net.ripe.rpki.validator3.storage.Bytes;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * TODO Either remove it of configure so that it works.
 *
 * @param <T>
 */
public class BsonCoder<T> implements Coder<T> {

    private final ObjectMapper mapper;

    @Data
    private static class Wrap {
        Object v;
        String c;

        public Wrap(Object v) {
            this.v = v;
            c = v.getClass().getName();
        }
    }

    public BsonCoder() {
        mapper = new ObjectMapper(new BsonFactory());
        mapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
    }

    @Override
    public ByteBuffer toBytes(T t) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            mapper.writeValue(baos, new Wrap(t));
            return Bytes.toDirectBuffer(baos.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public T fromBytes(ByteBuffer bb) {
        try {
            Wrap wrap = mapper.readValue(Bytes.toBytes(bb), Wrap.class);
            Class<T> c = (Class<T>) Class.forName(wrap.c);
            return c.cast(wrap.v);
        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

}
