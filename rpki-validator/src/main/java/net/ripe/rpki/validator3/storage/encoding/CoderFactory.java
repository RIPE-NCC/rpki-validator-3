package net.ripe.rpki.validator3.storage.encoding;

import net.ripe.rpki.validator3.storage.Bytes;
import net.ripe.rpki.validator3.storage.data.Ref;
import net.ripe.rpki.validator3.storage.data.RpkiObject;
import net.ripe.rpki.validator3.storage.data.RpkiRepository;
import net.ripe.rpki.validator3.storage.data.TrustAnchor;
import net.ripe.rpki.validator3.storage.data.validation.CertificateTreeValidationRun;
import net.ripe.rpki.validator3.storage.data.validation.RrdpRepositoryValidationRun;
import net.ripe.rpki.validator3.storage.data.validation.RsyncRepositoryValidationRun;
import net.ripe.rpki.validator3.storage.data.validation.TrustAnchorValidationRun;
import net.ripe.rpki.validator3.storage.encoding.custom.CustomCoder;
import net.ripe.rpki.validator3.storage.encoding.custom.RefCoder;
import net.ripe.rpki.validator3.storage.encoding.custom.RpkiObjectCoder;
import net.ripe.rpki.validator3.storage.encoding.custom.RpkiRepositoryCoder;
import net.ripe.rpki.validator3.storage.encoding.custom.TrustAnchorCoder;
import net.ripe.rpki.validator3.storage.encoding.custom.validation.CTValidationRunCoder;
import net.ripe.rpki.validator3.storage.encoding.custom.validation.RRValidationRunCoder;
import net.ripe.rpki.validator3.storage.encoding.custom.validation.RSValidationRunCoder;
import net.ripe.rpki.validator3.storage.encoding.custom.validation.TAValidationRunCoder;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

public class CoderFactory {

    public static <T> Coder<T> defaultCoder() {
        return new FSTCoder<>();
    }

    public static <T> Coder<T> defaultCoder(Class<T> c) {
//        return new FSTCoder<>();
//        return new GsonCoder<>(c);
//        return new BsonCoder<>(c);

        final CustomCoder<T> cc = customCoder(c);
        return new Coder<T>() {
            @Override
            public ByteBuffer toBytes(T t) {
                return Bytes.toDirectBuffer(cc.toBytes(t));
            }

            @Override
            public T fromBytes(ByteBuffer bb) {
                return cc.fromBytes(Bytes.toBytes(bb));
            }
        };
    }

    private static Map<Class<?>, CustomCoder<?>> customCoders = registerCustomCoder();

    private static Map<Class<?>, CustomCoder<?>> registerCustomCoder() {
        final Map<Class<?>, CustomCoder<?>> cc = new HashMap<>();
        cc.put(Ref.class, new RefCoder());
        cc.put(RpkiObject.class, new RpkiObjectCoder());
        cc.put(RpkiRepository.class, new RpkiRepositoryCoder());
        cc.put(TrustAnchor.class, new TrustAnchorCoder());
        cc.put(CertificateTreeValidationRun.class, new CTValidationRunCoder());
        cc.put(TrustAnchorValidationRun.class, new TAValidationRunCoder());
        cc.put(RsyncRepositoryValidationRun.class, new RSValidationRunCoder());
        cc.put(RrdpRepositoryValidationRun.class, new RRValidationRunCoder());
        return cc;
    }

    private static <T> CustomCoder<T> customCoder(Class<T> c) {
        CustomCoder<?> coder = customCoders.get(c);
        if (coder == null) {
            throw new IllegalArgumentException("There's no coder for class " + c);
        }
        return (CustomCoder<T>) coder;
    }

}
