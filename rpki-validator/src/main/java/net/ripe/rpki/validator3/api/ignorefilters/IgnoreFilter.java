package net.ripe.rpki.validator3.api.ignorefilters;

import lombok.Value;

@Value
class IgnoreFilter {
    private String asn;
    private String prefix;
    private String comment;
}
