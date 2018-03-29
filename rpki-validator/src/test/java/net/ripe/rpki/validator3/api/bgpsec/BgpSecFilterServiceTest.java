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
package net.ripe.rpki.validator3.api.bgpsec;

import com.google.common.collect.ImmutableList;
import net.ripe.ipresource.Asn;
import net.ripe.rpki.validator3.IntegrationTest;
import net.ripe.rpki.validator3.domain.BgpSecFilter;
import net.ripe.rpki.validator3.domain.ValidatedRpkiObjects;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;

@RunWith(SpringRunner.class)
@IntegrationTest
public class BgpSecFilterServiceTest {

    @Autowired
    private BgpSecFilterService bgpSecFilterService;

    private static Asn ASN_1 = Asn.parse("AS123");
    private static Asn ASN_2 = Asn.parse("AS456");
    private static Asn ASN_3 = Asn.parse("AS222");
    private static Asn ASN_4 = Asn.parse("AS333");
    private static Asn ASN_5 = Asn.parse("AS444");

    public static final String SKI_1 = "nvln23d";
    public static final String SKI_2 = "plspdpc";
    public static final String SKI_3 = "09309j3";

    public static final String PK_1 = "xxxxxxx";
    public static final String PK_2 = "yyyyyyy";
    public static final String PK_3 = "zzzzzzz";

    public static final ValidatedRpkiObjects.RouterCertificate CERTIFICATE_1 = ValidatedRpkiObjects.RouterCertificate.of(
            ValidatedRpkiObjects.TrustAnchorData.of(1L, "Test TA 1"),
            ImmutableList.of(ASN_1.toString()), SKI_1, PK_1);

    public static final ValidatedRpkiObjects.RouterCertificate CERTIFICATE_2 = ValidatedRpkiObjects.RouterCertificate.of(
            ValidatedRpkiObjects.TrustAnchorData.of(2L, "Test TA 2"),
            ImmutableList.of(ASN_1.toString(), ASN_2.toString()), SKI_2, PK_2);

    public static final ValidatedRpkiObjects.RouterCertificate CERTIFICATE_3 = ValidatedRpkiObjects.RouterCertificate.of(
            ValidatedRpkiObjects.TrustAnchorData.of(3L, "Test TA 3"),
            ImmutableList.of(ASN_1.toString()), SKI_3, PK_3);

    @Test
    public void should_not_filter_out_unrelated_certificate() {
        final List<ValidatedRpkiObjects.RouterCertificate> certificates = Collections.singletonList(CERTIFICATE_1);
        final List<BgpSecFilter> filters = Collections.singletonList(new BgpSecFilter(12L, null, null));
        final Stream<ValidatedRpkiObjects.RouterCertificate> s = bgpSecFilterService.filterCertificates(certificates.stream(), filters);
        assertEquals(certificates, s.collect(Collectors.toList()));
    }

    @Test
    public void should_filter_out_one_certificate_by_asn() {
        final List<ValidatedRpkiObjects.RouterCertificate> certificates = Arrays.asList(CERTIFICATE_1, CERTIFICATE_2);
        final List<BgpSecFilter> filters = Collections.singletonList(new BgpSecFilter(ASN_2.longValue(), null, null));
        final Stream<ValidatedRpkiObjects.RouterCertificate> s = bgpSecFilterService.filterCertificates(certificates.stream(), filters);
        assertEquals(Collections.singletonList(CERTIFICATE_1), s.collect(Collectors.toList()));
    }

    @Test
    public void should_filter_out_one_certificate_by_ski() {
        final List<ValidatedRpkiObjects.RouterCertificate> certificates = Collections.singletonList(CERTIFICATE_1);
        final List<BgpSecFilter> filters = Collections.singletonList(new BgpSecFilter(null, SKI_1, null));
        final Stream<ValidatedRpkiObjects.RouterCertificate> s = bgpSecFilterService.filterCertificates(certificates.stream(), filters);
        assertEquals(Collections.emptyList(), s.collect(Collectors.toList()));
    }

    @Test
    public void should_filter_out_one_certificate_by_asn_and_ski() {
        final List<ValidatedRpkiObjects.RouterCertificate> certificates = Arrays.asList(CERTIFICATE_1, CERTIFICATE_3);
        final List<BgpSecFilter> filters = Collections.singletonList(new BgpSecFilter(ASN_1.longValue(), SKI_3, null));
        final Stream<ValidatedRpkiObjects.RouterCertificate> s = bgpSecFilterService.filterCertificates(certificates.stream(), filters);
        assertEquals(Collections.singletonList(CERTIFICATE_1), s.collect(Collectors.toList()));
    }
}