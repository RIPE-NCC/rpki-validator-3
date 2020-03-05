/**
 * The BSD License
 *
 * Copyright (c) 2010-2018 RIPE NCC
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *   - Redistributions of source code must retain the above copyright notice,
 *     this list of conditions and the following disclaimer.
 *   - Redistributions in binary form must reproduce the above copyright notice,
 *     this list of conditions and the following disclaimer in the documentation
 *     and/or other materials provided with the distribution.
 *   - Neither the name of the RIPE NCC nor the names of its contributors may be
 *     used to endorse or promote products derived from this software without
 *     specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
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

    public static final String VALIDITY_PERIOD = "Validity Period";
    public static final String VALIDITY_PERIOD_EXAMPLE = "2018-06-04T05:53:04.000Z - 2018-06-11T05:58:04.000Z";

    public static final String SERIAL_NUMBER = "Serial Number";
    public static final String SERIAL_NUMBER_EXAMPLE = "4398793361";

    public static final String MAXLENGTH_PROPERTY = "Maxlength (>= prefix size)";
}
