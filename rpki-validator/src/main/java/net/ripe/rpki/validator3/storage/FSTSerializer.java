package net.ripe.rpki.validator3.storage;

import org.nustaq.serialization.simpleapi.DefaultCoder;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.util.Set;

import static net.ripe.rpki.validator3.storage.Bytes.toDirectBuffer;

@Component
public class FSTSerializer<T> implements Serializer<T> {

    private DefaultCoder coder;

    public FSTSerializer() throws ClassNotFoundException {
        registerSerializers();
    }

    public void registerSerializers() throws ClassNotFoundException {
        final ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Binary.class));
        Set<BeanDefinition> candidateComponents = scanner.findCandidateComponents("net.ripe.rpki.validator3.storage.data");
        final Class[] registered = new Class[candidateComponents.size()];
        int i = 0;
        for (BeanDefinition bd : candidateComponents) {
            registered[i++] = Class.forName(bd.getBeanClassName());
        }
        coder = new DefaultCoder(true, registered);
    }

    @Override
    public ByteBuffer toBytes(T t) {
        return toDirectBuffer(coder.toByteArray(t));
    }

    @Override
    @SuppressWarnings("unchecked")
    public T fromBytes(ByteBuffer bb) {
        byte[] bytes = new byte[bb.remaining()];
        bb.get(bytes);
        return (T) coder.toObject(bytes);
    }
}
