package net.ripe.rpki.validator3.storage.encoding;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.undercouch.bson4jackson.BsonFactory;
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

    private final Class<T> class_;

    public BsonCoder(Class<T> class_) {
        this.class_ = class_;
        mapper = new ObjectMapper(new BsonFactory());
        mapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
    }

    @Override
    public ByteBuffer toBytes(T t) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            mapper.writeValue(baos, t);
            return Bytes.toDirectBuffer(baos.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public T fromBytes(ByteBuffer bb) {
        try {
            return mapper.readValue(Bytes.toBytes(bb), class_);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
