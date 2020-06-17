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
package net.ripe.rpki.validator3.util;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.File;
import java.net.URI;

import static org.assertj.core.api.BDDAssertions.then;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class TrustAnchorLocatorTest {

    public static final String RIPE_NCC_PUBLIC_KEY = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA0URYSGqUz2myBsOzeW1j" +
            "Q6NsxNvlLMyhWknvnl8NiBCs/T/S2XuNKQNZ+wBZxIgPPV2pFBFeQAvoH/WK83Hw" +
            "A26V2siwm/MY2nKZ+Olw+wlpzlZ1p3Ipj2eNcKrmit8BwBC8xImzuCGaV0jkRB0G" +
            "Z0hoH6Ml03umLprRsn6v0xOP0+l6Qc1ZHMFVFb385IQ7FQQTcVIxrdeMsoyJq9eM" +
            "kE6DoclHhF/NlSllXubASQ9KUWqJ0+Ot3QCXr4LXECMfkpkVR2TZT+v5v658bHVs" +
            "6ZxRD1b6Uk1uQKAyHUbn/tXvP8lrjAibGzVsXDT2L0x4Edx+QdixPgOji3gBMyL2" +
            "VwIDAQAB";
    public static final String AFRINIC_PUBLIC_KEY = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAxsAqAhWIO+ON2Ef9oRDM" +
            "pKxv+AfmSLIdLWJtjrvUyDxJPBjgR+kVrOHUeTaujygFUp49tuN5H2C1rUuQavTH" +
            "vve6xNF5fU3OkTcqEzMOZy+ctkbde2SRMVdvbO22+TH9gNhKDc9l7Vu01qU4LeJH" +
            "k3X0f5uu5346YrGAOSv6AaYBXVgXxa0s9ZvgqFpim50pReQe/WI3QwFKNgpPzfQL" +
            "6Y7fDPYdYaVOXPXSKtx7P4s4KLA/ZWmRL/bobw/i2fFviAGhDrjqqqum+/9w1hEl" +
            "L/vqihVnV18saKTnLvkItA/Bf5i11Yhw2K7qv573YWxyuqCknO/iYLTR1DToBZcZ" +
            "UQIDAQAB";

    @Test
    public void readStandardTrustAnchor_rsync_only_accepted() throws Exception {
        File talFile = new ClassPathResource("tals/rfc8630/ripe-ncc-rsync-only.tal").getFile();

        TrustAnchorLocator tal = TrustAnchorLocator.fromFile(talFile);

        then(tal.getCertificateLocations()).containsExactly(
                URI.create("rsync://rpki.ripe.net/ta/ripe-ncc-ta.cer")
        );

        then(tal.getPublicKeyInfo()).isEqualTo(RIPE_NCC_PUBLIC_KEY);
    }

    @Test
    public void readStandardTrustAnchor_https_rsync_accepted() throws Exception {
        File talFile = new ClassPathResource("tals/rfc8630/afrinic-https-rsync.tal").getFile();

        TrustAnchorLocator tal = TrustAnchorLocator.fromFile(talFile);

        then(tal.getCertificateLocations()).containsExactly(
                URI.create("https://rpki.afrinic.net/repository/AfriNIC.cer"),
                URI.create("rsync://rpki.afrinic.net/repository/AfriNIC.cer")
        );

        then(tal.getPublicKeyInfo()).isEqualTo(AFRINIC_PUBLIC_KEY);
    }

    @Test
    public void readStandardTrustAnchor_rsync_https_accepted() throws Exception {
        File talFile = new ClassPathResource("tals/rfc8630/afrinic-reversed-rsync-https.tal").getFile();

        TrustAnchorLocator tal = TrustAnchorLocator.fromFile(talFile);

        then(tal.getCertificateLocations()).containsExactly(
                URI.create("rsync://rpki.afrinic.net/repository/AfriNIC.cer"),
                URI.create("https://rpki.afrinic.net/repository/AfriNIC.cer")
        );

        then(tal.getPublicKeyInfo()).isEqualTo(AFRINIC_PUBLIC_KEY);
    }

    @Test
    public void readStandardTrustAnchor_with_http_location_rejected() throws Exception {
        File talFile = new ClassPathResource("tals/rfc8630/example-tal-with-http-and-rsync.tal").getFile();

        assertThrows(TrustAnchorExtractorException.class, () -> TrustAnchorLocator.fromFile(talFile));
    }

    @Test
    public void readExtendedTrustAnchor_rsync_only_accepted() throws Exception {
        File talFile = new ClassPathResource("tals/ripeextended/ripe-ncc-rsync-only.tal").getFile();

        TrustAnchorLocator tal = TrustAnchorLocator.fromFile(talFile);

        then(tal.getCertificateLocations()).containsExactly(
                URI.create("rsync://rpki.ripe.net/ta/ripe-ncc-ta.cer")
        );

        then(tal.getPublicKeyInfo()).isEqualTo(RIPE_NCC_PUBLIC_KEY);
    }

    @Test
    public void readExtendedTrustAnchor_https_rsync_accepted() throws Exception {
        File talFile = new ClassPathResource("tals/ripeextended/afrinic-https_rsync.tal").getFile();

        TrustAnchorLocator tal = TrustAnchorLocator.fromFile(talFile);

        then(tal.getCertificateLocations()).containsExactly(
                URI.create("https://rpki.afrinic.net/repository/AfriNIC.cer"),
                URI.create("rsync://rpki.afrinic.net/repository/AfriNIC.cer")
        );

        then(tal.getPublicKeyInfo()).isEqualTo(AFRINIC_PUBLIC_KEY);
    }

    @Test
    public void readExtendedTrustAnchor_rsync_https_accepted() throws Exception {
        File talFile = new ClassPathResource("tals/ripeextended/afrinic-rsync_https.tal").getFile();

        TrustAnchorLocator tal = TrustAnchorLocator.fromFile(talFile);

        then(tal.getCertificateLocations()).containsExactly(
                URI.create("rsync://rpki.afrinic.net/repository/AfriNIC.cer"),
                URI.create("https://rpki.afrinic.net/repository/AfriNIC.cer")
        );

        then(tal.getPublicKeyInfo()).isEqualTo(AFRINIC_PUBLIC_KEY);
    }

    @Test
    public void readExtendedTrustAnchor_with_http_location_rejected() throws Exception {
        File talFile = new ClassPathResource("tals/ripeextended/example-tal-with-http-and-rsync.tal").getFile();

        assertThrows(TrustAnchorExtractorException.class, () -> TrustAnchorLocator.fromFile(talFile));
    }
}
