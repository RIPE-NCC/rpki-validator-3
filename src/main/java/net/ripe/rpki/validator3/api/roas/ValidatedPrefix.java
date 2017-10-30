package net.ripe.rpki.validator3.api.roas;

import lombok.Value;
import net.ripe.rpki.commons.crypto.cms.roa.RoaCms;

import java.util.stream.Stream;

@Value(staticConstructor = "of")
public class ValidatedPrefix {
    String prefix;
    Integer maximumLength;
    int effectiveMaximumLength;
    long asn;

    public static Stream<ValidatedPrefix> of(RoaCms roaCms) {
        long asn = roaCms.getAsn().longValue();
        return roaCms.getPrefixes().stream()
            .map(prefix -> ValidatedPrefix.of(
                prefix.getPrefix().toString(),
                prefix.getMaximumLength(),
                prefix.getEffectiveMaximumLength(),
                asn
            ));
    }
}
