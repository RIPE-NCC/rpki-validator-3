package net.ripe.rpki.validator3.storage;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.ByteBufferInput;
import com.esotericsoftware.kryo.io.ByteBufferOutput;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.nio.ByteBuffer;

@Component
public class BinarySerializer<T> implements Serializer<T> {

    private final Kryo kryo = new Kryo();

    @PostConstruct
    public void registerSerializers() throws ClassNotFoundException {
        final ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Binary.class));
        for (BeanDefinition bd : scanner.findCandidateComponents("net.ripe.rpki.validator3.storage.data")) {
            kryo.register(Class.forName(bd.getBeanClassName()));
        }
    }

    @Override
    public ByteBuffer toBytes(T t) {
        final ByteBufferOutput bbo = new ByteBufferOutput();
        kryo.writeObject(bbo, t);
        return bbo.getByteBuffer();
    }

    @Override
    public T fromBytes(ByteBuffer bb, Class<T> c) {
        return kryo.readObject(new ByteBufferInput(bb), c);
    }
}
