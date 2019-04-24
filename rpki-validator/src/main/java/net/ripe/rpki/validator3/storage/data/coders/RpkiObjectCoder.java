package net.ripe.rpki.validator3.storage.data.coders;

import net.ripe.rpki.validator3.storage.data.RpkiObject;

import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.Function;

public class RpkiObjectCoder implements Coder<RpkiObject> {

    private static final Tags tags = new Tags();
    private final static short TYPE_TAG = tags.unique(11);
    private final static short SHA256_TAG = tags.unique(12);
    private final static short AKI_TAG = tags.unique(13);
    private final static short LAST_MARKED_TAG = tags.unique(14);
    private final static short SERIAL_TAG = tags.unique(15);
    private final static short ENCODED_TAG = tags.unique(16);
    private final static short SIGNING_TIME_TAG = tags.unique(17);
    private final static short LOCATIONS_TAG = tags.unique(18);
    private final static short ROA_PREFIXES = tags.unique(19);

    private final static RoaPrefixCoder roaPrefixCoder = new RoaPrefixCoder();

    @Override
    public byte[] toBytes(RpkiObject rpkiObject) {
        final Encoded encoded = new Encoded();

        BaseCoder.toBytes(rpkiObject, encoded);

        encoded.append(TYPE_TAG, Coders.toBytes(rpkiObject.getType().name()));
        encoded.append(SHA256_TAG, rpkiObject.getSha256());
        encoded.append(AKI_TAG, rpkiObject.getAuthorityKeyIdentifier());
        encoded.appendNotNull(rpkiObject.getSerialNumber(), SERIAL_TAG, Coders::toBytes);
        encoded.appendNotNull(rpkiObject.getEncoded(), ENCODED_TAG, Function.identity());
        encoded.appendNotNull(rpkiObject.getSigningTime(), SIGNING_TIME_TAG, Coders::toBytes);
        encoded.appendNotNull(rpkiObject.getLastMarkedReachableAt(), LAST_MARKED_TAG, Coders::toBytes);

        if (rpkiObject.getLocations() != null && !rpkiObject.getLocations().isEmpty()) {
            byte[] locationBytes = Coders.toBytes(rpkiObject.getLocations(), Coders::toBytes);
            encoded.append(LOCATIONS_TAG, locationBytes);
        }
        if (rpkiObject.getRoaPrefixes() != null && !rpkiObject.getRoaPrefixes().isEmpty()) {
            byte[] prefixesBytes = Coders.toBytes(rpkiObject.getRoaPrefixes(), roaPrefixCoder::toBytes);
            encoded.append(ROA_PREFIXES, prefixesBytes);
        }

        return encoded.toByteArray();
    }

    @Override
    public RpkiObject fromBytes(byte[] bytes) {
        Map<Short, byte[]> content = Encoded.fromByteArray(bytes).getContent();

        final RpkiObject rpkiObject = new RpkiObject();
        BaseCoder.fromBytes(content, rpkiObject);

        rpkiObject.setType(Coders.toString(content.get(TYPE_TAG)));
        rpkiObject.setSha256(content.get(SHA256_TAG));
        rpkiObject.setEncoded(content.get(ENCODED_TAG));
        rpkiObject.setAuthorityKeyIdentifier(content.get(AKI_TAG));
        Encoded.field(content, LAST_MARKED_TAG).ifPresent(b -> rpkiObject.setLastMarkedReachableAt(Coders.toInstant(b)));
        Encoded.field(content, SIGNING_TIME_TAG).ifPresent(b -> rpkiObject.setSigningTime(Coders.toInstant(b)));
        Encoded.field(content, SERIAL_TAG).ifPresent(b -> rpkiObject.setSerialNumber(Coders.toBigInteger(b)));

        Encoded.field(content, LOCATIONS_TAG).ifPresent(b -> {
            final List<String> objects = Coders.fromBytes(b, Coders::toString);
            rpkiObject.setLocations(new TreeSet<>(objects));
        });

        Encoded.field(content, ROA_PREFIXES).ifPresent(b ->
                rpkiObject.setRoaPrefixes(Coders.fromBytes(b, roaPrefixCoder::fromBytes)));

        return rpkiObject;
    }

}
