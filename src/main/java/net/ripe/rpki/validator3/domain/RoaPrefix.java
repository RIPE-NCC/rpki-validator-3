package net.ripe.rpki.validator3.domain;

import lombok.Data;
import net.ripe.ipresource.Asn;
import net.ripe.ipresource.IpRange;

import javax.persistence.Basic;
import javax.persistence.Embeddable;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

@Data
@Embeddable
public class RoaPrefix {
    @Basic
    @NotNull
    @NotEmpty
    String prefix;

    @Basic
    Integer maximumLength;

    @Basic
    int effectiveLength;

    @Basic
    long asn;

    public static RoaPrefix of(IpRange prefix, Integer maximumLength, Asn asn) {
        RoaPrefix result = new RoaPrefix();
        result.setPrefix(prefix.toString());
        result.setMaximumLength(maximumLength);
        result.setEffectiveLength(maximumLength != null ? maximumLength : prefix.getPrefixLength());
        result.setAsn(asn.longValue());
        return result;
    }
}
