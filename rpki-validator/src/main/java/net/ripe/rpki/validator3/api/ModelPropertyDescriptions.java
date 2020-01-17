package net.ripe.rpki.validator3.api;

public class ModelPropertyDescriptions {
    public static final String ASN_EXAMPLE = "3333";
    public static final String ASN_PROPERTY = "ASN to match (without AS prefix)";

    public static final String ASN_PREFIXED_PROPERTY = "ASN to match (with AS prefix)";
    public static final String ASN_PREFIXED_EXAMPLE = "AS3333";

    public static final String ASN_LIST_PROPERTY = "List of ASNs (without prefix)";

    public static final String ORIGIN_PROPERTY = "Origin AS (without AS prefix)";
    public static final String ORIGIN_PREFIXED_PROPERTY = "Origin AS (with AS prefix)";

    public static final String PREFIX_EXAMPLE = "193.0.0.0/21";

    /**
     * The inputs for @see net.ripe.rpki.validator3.api.Sorting#parse
     */
    public static final String SORT_DIRECTION_ALLOWABLE_VALUES = "asc, desc";
    public static final String SORT_BY_ALLOWABLE_VALUES = "prefix, asn, ta, key, location, status, validity, type, lastchecked, comment, maximumlength";

    /**
     * @see net.ripe.rpki.validator3.api.bgp.BgpPreviewService.Validity
     */
    public static final String VALIDITY_ALLOWABLE_VALUES = "UNKNOWN, VALID, INVALID_ASN, INVALID_LENGTH";

    public static final String SOURCE_TRUST_ANCHOR = "source (name of Trust Anchor)";

    public static final String TRUST_ANCHOR = "Name of trust anchor";
    public static final String TRUST_ANCHOR_EXAMPLE = "RIPE NCC RPKI Root";

    public static final String MAXLENGTH_PROPERTY = "Maxlength (>= prefix size)";
}
