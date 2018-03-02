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

import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.validator3.util.Http;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.function.Function;

import static org.springframework.util.StreamUtils.copy;

@Component
@Slf4j
public class HttpRrdpClient implements RrdpClient {

    @Value("${rpki.validator.rrdp.trust.all.tls.certificates}")
    private boolean trustAllTlsCertificates;

    private HttpClient httpClient;

    @PostConstruct
    public void postConstruct() throws Exception {
        final SslContextFactory sslContextFactory = new SslContextFactory();
        sslContextFactory.setTrustAll(trustAllTlsCertificates);
        httpClient = new HttpClient(sslContextFactory);
        httpClient.start();
    }

    @Override
    public <T> T readStream(final String uri, Function<InputStream, T> reader) {
        return Http.readStream(() -> httpClient.newRequest(uri), reader);
    }

    @Override
    public byte[] getBody(String uri) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        readStream(uri, s -> {
            try {
                return copy(s, baos);
            } catch (IOException e) {
                throw new RrdpException("error reading response body for " + uri + ": " + e, e);
            }
        });
        return baos.toByteArray();
    }
}
