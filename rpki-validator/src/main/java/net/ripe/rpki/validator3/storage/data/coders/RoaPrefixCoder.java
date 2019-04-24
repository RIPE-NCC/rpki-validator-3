package net.ripe.rpki.validator3.storage.data.coders;

import net.ripe.ipresource.Asn;
import net.ripe.ipresource.IpRange;
import net.ripe.rpki.validator3.api.bgp.PackedIpRange;
import net.ripe.rpki.validator3.storage.data.RoaPrefix;

import java.util.Map;

public class RoaPrefixCoder implements Coder<RoaPrefix> {

    private static final Tags tags = new Tags();
    private final static short PREFIX_TAG = tags.unique(1);
    private final static short ASN_TAG = tags.unique(2);
    private final static short MAX_LEN_TAG = tags.unique(3);

    @Override
    public byte[] toBytes(RoaPrefix roaPrefix) {
        final Encoded encoded = new Encoded();
        BaseCoder.toBytes(roaPrefix, encoded);
        encoded.append(PREFIX_TAG, new PackedIpRange(roaPrefix.getPrefix()).getContent());
        encoded.append(ASN_TAG, Coders.toBytes(roaPrefix.getAsn()));
        if (roaPrefix.getMaximumLength() != null) {
            encoded.append(MAX_LEN_TAG, Coders.toBytes(roaPrefix.getMaximumLength()));
        }
        return encoded.toByteArray();
    }

    @Override
    public RoaPrefix fromBytes(byte[] bytes) {
        Map<Short, byte[]> content = Encoded.fromByteArray(bytes).getContent();
        IpRange prefix = PackedIpRange.of(content.get(PREFIX_TAG));
        long asn = Coders.toLong(content.get(ASN_TAG));
        byte[] maxLen = content.get(MAX_LEN_TAG);
        Integer maximumLength = maxLen != null ? Coders.toInt(maxLen) : null;

        RoaPrefix roaPrefix = RoaPrefix.of(prefix, maximumLength, new Asn(asn));
        BaseCoder.fromBytes(content, roaPrefix);
        return roaPrefix;
    }

}
