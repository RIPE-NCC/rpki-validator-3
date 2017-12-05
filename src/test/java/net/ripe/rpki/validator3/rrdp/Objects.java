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
package net.ripe.rpki.validator3.rrdp;

import net.ripe.rpki.validator3.util.Hex;
import org.apache.commons.lang.StringEscapeUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Base64;

import static org.apache.commons.lang.StringEscapeUtils.escapeXml;

public class Objects {

    // TODO Generate it randomly
    public static byte[] aValidManifest() {
        return decode("MIAGCSqGSIb3DQEHAqCAMIACAQMxDzANBglghkgBZQMEAgEFADCABgsqhkiG9w0BCRABGqCAJIAE\n" +
                "        gYkwgYYCAguWGA8yMDE0MTIwMzE4MDg0MFoYDzIwMTQxMjA0MTgwODQwWgYJYIZIAWUDBAIBMFMw\n" +
                "        URYsNjcxNTcwZjA2NDk5ZmJkMmQ2YWI3NmM0ZjIyNTY2ZmU0OWQ1ZGU2MC5jcmwDIQD1h9mcwKzN\n" +
                "        70He/gIMVxszGJlIXLh/TGzkaNTXxLixmwAAAAAAAKCAMIIFGjCCBAKgAwIBAgICC5YwDQYJKoZI\n" +
                "        hvcNAQELBQAwMzExMC8GA1UEAxMoNjcxNTcwZjA2NDk5ZmJkMmQ2YWI3NmM0ZjIyNTY2ZmU0OWQ1\n" +
                "        ZGU2MDAeFw0xNDEyMDMxODA4NDBaFw0xNDEyMTAxODA4NDBaMDMxMTAvBgNVBAMTKDMzMTI1YzA4\n" +
                "        NmEwOWEyOTJkYzQxMWI1MzkwODA2ZDA4NTMxM2E1MTQwggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAw\n" +
                "        ggEKAoIBAQCak1G7912fuezSWPpPqER3aZaP4HShGiIiRWeJLOYKpklAeSO8kRa9R+yDCa9CMi1B\n" +
                "        lewW/C5Coomb9teVZRg31YkpZlXqqNGg8GVesNMJX3ryuizQ+WRUcgwJoakqWH7wPu5zdfFj4Cpk\n" +
                "        BpgJF+6TBYwXTjAmxfFP0hm0QLWCLxEd1gBGEdBmOogHqfOZHU95GLjllzsPRmR13kyh7BbYMie+\n" +
                "        ENJAqqKBlQvW86xPEDMJKUc0uQDnTPCZQBqFwE1xrgUAuSCfJMUguAE8clsshOFe8ROF9t6NIBxK\n" +
                "        oxA+PTYCpcthCBtdCFyWn/SLp1pb3gIA6xP9ESGlRHNumPL3AgMBAAGjggI2MIICMjAdBgNVHQ4E\n" +
                "        FgQUMxJcCGoJopLcQRtTkIBtCFMTpRQwHwYDVR0jBBgwFoAUZxVw8GSZ+9LWq3bE8iVm/knV3mAw\n" +
                "        DgYDVR0PAQH/BAQDAgeAMGcGCCsGAQUFBwEBBFswWTBXBggrBgEFBQcwAoZLcnN5bmM6Ly9iYW5k\n" +
                "        aXRvLnJpcGUubmV0L3JlcG8vM2E4N2E0YjEtNmUyMi00YTYzLWFkMGYtMDZmODNhZDNjYTE2L2Rl\n" +
                "        ZmF1bHQvMIGWBggrBgEFBQcBCwSBiTCBhjCBgwYIKwYBBQUHMAuGd3JzeW5jOi8vYmFuZGl0by5y\n" +
                "        aXBlLm5ldC9yZXBvLzNhODdhNGIxLTZlMjItNGE2My1hZDBmLTA2ZjgzYWQzY2ExNi9kZWZhdWx0\n" +
                "        LzY3MTU3MGYwNjQ5OWZiZDJkNmFiNzZjNGYyMjU2NmZlNDlkNWRlNjAubWZ0MIGJBgNVHR8EgYEw\n" +
                "        fzB9oHugeYZ3cnN5bmM6Ly9iYW5kaXRvLnJpcGUubmV0L3JlcG8vM2E4N2E0YjEtNmUyMi00YTYz\n" +
                "        LWFkMGYtMDZmODNhZDNjYTE2L2RlZmF1bHQvNjcxNTcwZjA2NDk5ZmJkMmQ2YWI3NmM0ZjIyNTY2\n" +
                "        ZmU0OWQ1ZGU2MC5jcmwwGAYDVR0gAQH/BA4wDDAKBggrBgEFBQcOAjAhBggrBgEFBQcBBwEB/wQS\n" +
                "        MBAwBgQCAAEFADAGBAIAAgUAMBUGCCsGAQUFBwEIAQH/BAYwBKACBQAwDQYJKoZIhvcNAQELBQAD\n" +
                "        ggEBAEO1dSFDN4wZqtZ0fWo5G0YVN+mtk6tKhHPFwX7ydTofnHZkE2pO7C93XcgPcP4zLUBPt5kS\n" +
                "        aH+0vcBxs9Vg//58cHRUEHhls9O/XcS8RXCVkNiga+9NB5s4oi0+i/gDU3eOUqE/jqSJAJAS+Ehi\n" +
                "        tvNh0LuLrW92NrOfbYDk29how3uxK4JucIAQ05i63l7EAeQp3WeI8nVzB9Rfrkv+PSV+57mSXXtJ\n" +
                "        /jWu3kyjvsxRjeUL3Im2Z1F48zfVF6pVaDT7ib4YbKOyAQTMpi4W6NZwgQskda9B8/0qV/d+2JrC\n" +
                "        m3Ozm0t2laoH8xKP/OC33bBXLCxUvkVqvB/Y+TUXfAEAADGCAawwggGoAgEDgBQzElwIagmiktxB\n" +
                "        G1OQgG0IUxOlFDANBglghkgBZQMEAgEFAKBrMBoGCSqGSIb3DQEJAzENBgsqhkiG9w0BCRABGjAc\n" +
                "        BgkqhkiG9w0BCQUxDxcNMTQxMjAzMTgwODQwWjAvBgkqhkiG9w0BCQQxIgQgdNPMbp9lJJHNMmIz\n" +
                "        00ff73VkVFYWo2Uf6/b4zIzFZucwDQYJKoZIhvcNAQEBBQAEggEAXHNHm+DUD1s9IQMewvKsoNGi\n" +
                "        fXL2jG3yfuGys5x1aJji3bIKGiU+weHmnP9aoH9UFRLk6pW1wFOS0+6M87UD8cU17w9F10e0258S\n" +
                "        9p7xHMgbrYqXrX9OucMqiN4M+ThDzyDXnfNAOgw5XNJu9KRndS9vyXS6lcvD7JTOhkyqKsrqHXlM\n" +
                "        0pX+rYFtrF2RNjB54veooSkcKGojXReLttZbvVKWKwkVg2RJy4tt7MOGU0Q6qa/J5S7O6xvwPjkY\n" +
                "        yCFvrHm+CgeXoR/3Hg/Rk/NdsK4K1u5dXhRh3KYv4P/hnGSD83aFE9t/DTicvl6SjaXFCtLtJlTX\n" +
                "        BqSW7wgZ6OoLxwAAAAAAAA==");
    }

    public static byte[] aParseableCrl() {
        return decode("MIIBxTCBrgIBATANBgkqhkiG9w0BAQsFADAzMTEwLwYDVQQDEyg2NzE1NzBmMDY0OTlmYmQyZDZh\n" +
                "        Yjc2YzRmMjI1NjZmZTQ5ZDVkZTYwFw0xNDEyMDMxODA4NDBaFw0xNDEyMDQxODA4NDBaMBUwEwIC\n" +
                "        C5UXDTE0MTIwMzE4MDg0MFqgMDAuMB8GA1UdIwQYMBaAFGcVcPBkmfvS1qt2xPIlZv5J1d5gMAsG\n" +
                "        A1UdFAQEAgILljANBgkqhkiG9w0BAQsFAAOCAQEAIbL+8connmKLeypzs/P6FOHv8elmLp6dFlId\n" +
                "        SDpZT7p6y9xLZkvuow39XOs6NB1AOA+92uao9hEV1XuEBGP98nsx0frL8HJtKcEn0q5LGqA4YeBG\n" +
                "        n28+Ldvlh4DetiKvFpsKW/VYqjRumHcgTdWpESY/f9hH3xW6JCggH5cFGFF/dCsCdGT1v+m53zf4\n" +
                "        Dlz8KhRDEaok3UMycX9XUWMB5HSwf05Qrha2LIFf66uk6AQQEmV9ZiBq3IdbkdNd90TIVDMvnSW/\n" +
                "        p9Xygdx8azaE2+hsOc9J7+E2kBuu4isLhvfZmChtFpxIUrljQRD4iUil8/xmB6MAIptoF1EslpAI\n" +
                "        aw==");
    }

    public static byte[] aParseableCertificate() {
        return decode("MIIFNDCCBBygAwIBAgIBAjANBgkqhkiG9w0BAQsFADANMQswCQYDVQQDEwJUQTAeFw0xNDExMTMw\n" +
                "        MzU4MjlaFw0xNTExMTMwMzU4MjlaMDMxMTAvBgNVBAMTKDY3MTU3MGYwNjQ5OWZiZDJkNmFiNzZj\n" +
                "        NGYyMjU2NmZlNDlkNWRlNjAwggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQDOlUYxDPwu\n" +
                "        hqVSG5VXcg96qTYt9aKOH8qV2lAU/jnY1rRl2W5Uoa8RrAIseou8ltLKonMcVulHyoyY+J9GqrzN\n" +
                "        45vRSgBaOuvLn6nTuoD0LQsD/m8c/wEmFjQllirxQykLGJLXn1eKdUs/OXGgrAUPzgvkciJdsg69\n" +
                "        6X44deHcbCU0ZQZSLxZBZEqjfgyoYgww9n/hK5Sfkb44LsBK1lESdBSRrTpFizrCxl22ptsH0eW4\n" +
                "        ek80CV5YgCg4F4u9xlzS2DvB+1X3Nl1vvTZ6TJlpVjIVcvE+sKQ50ntUwWG1+lOJc+twRehhiCAb\n" +
                "        yHhfaxID4B+7h5Rcpkh1Q1AUMG9JAgMBAAGjggJ3MIICczAdBgNVHQ4EFgQUZxVw8GSZ+9LWq3bE\n" +
                "        8iVm/knV3mAwHwYDVR0jBBgwFoAUd4IboVLl+9bEbD6VrCsnqRClFNUwDwYDVR0TAQH/BAUwAwEB\n" +
                "        /zAOBgNVHQ8BAf8EBAMCAQYwRQYIKwYBBQUHAQEEOTA3MDUGCCsGAQUFBzAChilodHRwOi8vYmFu\n" +
                "        ZGl0by5yaXBlLm5ldC9ycGtpLWNhL3RhL3RhLmNlcjCCATAGCCsGAQUFBwELBIIBIjCCAR4wVwYI\n" +
                "        KwYBBQUHMAWGS3JzeW5jOi8vYmFuZGl0by5yaXBlLm5ldC9yZXBvLzNhODdhNGIxLTZlMjItNGE2\n" +
                "        My1hZDBmLTA2ZjgzYWQzY2ExNi9kZWZhdWx0LzCBgwYIKwYBBQUHMAqGd3JzeW5jOi8vYmFuZGl0\n" +
                "        by5yaXBlLm5ldC9yZXBvLzNhODdhNGIxLTZlMjItNGE2My1hZDBmLTA2ZjgzYWQzY2ExNi9kZWZh\n" +
                "        dWx0LzY3MTU3MGYwNjQ5OWZiZDJkNmFiNzZjNGYyMjU2NmZlNDlkNWRlNjAubWZ0MD0GCCsGAQUF\n" +
                "        BzANhjFodHRwOi8vYmFuZGl0by5yaXBlLm5ldC9ycGtpLWNhL25vdGlmeS9ub3RpZnkueG1sMFsG\n" +
                "        A1UdHwRUMFIwUKBOoEyGSnJzeW5jOi8vYmFuZGl0by5yaXBlLm5ldC9yZXBvLzc3ODIxYmExNTJl\n" +
                "        NWZiZDZjNDZjM2U5NWFjMmIyN2E5MTBhNTE0ZDUuY3JsMBgGA1UdIAEB/wQOMAwwCgYIKwYBBQUH\n" +
                "        DgIwHgYIKwYBBQUHAQcBAf8EDzANMAsEAgABMAUDAwDAqDANBgkqhkiG9w0BAQsFAAOCAQEAkAnl\n" +
                "        E+Fm1r3cmW8EEwhq4Wo37j7qC8ciU/E/zJqptROd8M8+2PDjCF8K7plf/SqYNUWjCk8zQv7Siala\n" +
                "        DP3JNI7oWkJ5K9zSU/qPGD8UbrfK5EF4g+++OAsxsOf/qeMVdZ6FlPIUV0wYj2s9w1zz/r16HFV6\n" +
                "        QO785ajB50foqo/oQ74BSRbrlYkWrM8U45rdSiAMlyr0lHgv0OCqNK6AVR6y9Sp6bBUi7RotZ5FN\n" +
                "        x0TgBRTA6xp4pjG5FimX1SanMaW1hgYqdc4X5aZ9gPiyqvBcOtFq91WnNTsm5Ox0cPNDCkMPLAwW\n" +
                "        pHOiFA0PlD0vBPrvTR1hsgfKGd318Qzq+w==");
    }

    private static byte[] decode(String s) {
        return Base64.getDecoder().decode(s.replaceAll("\\s", ""));
    }

    private static String encode(byte[] s) {
        return new String(Base64.getEncoder().encode(s));
    }

    static InputStream fileIS(final String path) throws IOException {
        return Thread.currentThread().getContextClassLoader().getResourceAsStream(path);
    }

    static byte[] notificationXml(long serial, String sessionId, SnapshotInfo snapshot, DeltaInfo... deltas) {
        final StringBuilder sb = new StringBuilder();
        sb.append("<notification xmlns=\"HTTP://www.ripe.net/rpki/rrdp\" version=\"1\" session_id=\"").append(sessionId).append("\" serial=\"").append(serial).append("\">");
        sb.append("    <snapshot uri=\"").append(escapeXml(snapshot.uri)).append("\" hash=\"").append(Hex.format(snapshot.hash)).append("\"/>");
        for (DeltaInfo di : deltas) {
            sb.append("  <delta uri=\"").append(escapeXml(di.uri)).append("\" hash=\"").append(Hex.format(di.hash)).append("\" serial=\"").append(di.serial).append("\"/>");
        }
        sb.append("</notification>");
        return getBytes(sb);
    }

    static byte[] snapshotXml(long serial, String sessionId, Publish... publishes) {
        final StringBuilder sb = new StringBuilder();
        sb.append("<snapshot xmlns=\"http://www.ripe.net/rpki/rrdp\" version=\"1\" session_id=\"").append(sessionId).append("\" serial=\"").append(serial).append("\">");
        for (Publish publish : publishes) {
            sb.append(publishXml(publish));
        }
        sb.append("</snapshot>");
        return getBytes(sb);
    }

    static byte[] deltaXml(long serial, String sessionId, Change... updates) {
        final StringBuilder sb = new StringBuilder();
        sb.append("<delta xmlns=\"http://www.ripe.net/rpki/rrdp\" version=\"1\" session_id=\"").append(sessionId).append("\" serial=\"").append(serial).append("\">");
        for (final Change change : updates) {
            if (change instanceof DeltaPublish) {
                sb.append(publishXml((DeltaPublish) change));
            } else if (change instanceof DeltaWithdraw) {
                sb.append(withdrawXml((DeltaWithdraw) change));
            }
        }
        sb.append("</delta>");
        return getBytes(sb);
    }

    private static String publishXml(Publish publish) {
        return "  <publish uri=\"" + escapeXml(publish.uri) + "\">\n    " + encode(publish.content) + "\n</publish>\n";
    }

    private static String publishXml(DeltaPublish publish) {
        if (publish.hash != null) {
            return "  <publish uri=\"" + escapeXml(publish.uri) + "\" hash=\"" + Hex.format(publish.hash) + "\">\n    " + encode(publish.content) + "\n</publish>\n";
        }
        return "  <publish uri=\"" + escapeXml(publish.uri) + "\">\n    " + encode(publish.content) + "\n</publish>\n";
    }

    private static String withdrawXml(DeltaWithdraw withdraw) {
        return "  <withdraw uri=\"" + escapeXml(withdraw.uri) + "\" hash=\"" + Hex.format(withdraw.hash) + "\"/>";
    }

    private static byte[] getBytes(StringBuilder sb) {
        return sb.toString().getBytes(Charset.forName("UTF-8"));
    }

    static class SnapshotInfo {
        public final String uri;
        public final byte[] hash;

        SnapshotInfo(String uri, byte[] hash) {
            this.uri = uri;
            this.hash = hash;
        }
    }

    static class DeltaInfo {
        public final String uri;
        public final byte[] hash;
        public final long serial;

        DeltaInfo(String uri, byte[] hash, long serial) {
            this.uri = uri;
            this.hash = hash;
            this.serial = serial;
        }
    }

    static class Publish {
        public final String uri;
        public final byte[] content;

        Publish(String uri, byte[] content) {
            this.uri = uri;
            this.content = content;
        }
    }

    static class Change {
        public final String uri;

        private Change(String uri) {
            this.uri = uri;
        }
    }

    static class DeltaPublish extends Change {
        public final byte[] hash;
        public final byte[] content;

        DeltaPublish(String uri, byte[] hash, byte[] content) {
            super(uri);
            this.hash = hash;
            this.content = content;
        }

        DeltaPublish(String uri, byte[] content) {
            super(uri);
            hash = null;
            this.content = content;
        }
    }

    private static class DeltaWithdraw extends Change {
        public final byte[] hash;

        private DeltaWithdraw(String uri, byte[] hash) {
            super(uri);
            this.hash = hash;
        }
    }
}
