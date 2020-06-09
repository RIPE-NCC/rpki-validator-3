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
package net.ripe.rpki.validator3.config;

import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.validator3.api.util.BuildInformation;
import net.ripe.rpki.validator3.util.HappyEyeballsResolver;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.HttpProxy;
import org.eclipse.jetty.client.ProxyConfiguration;
import org.eclipse.jetty.http.HttpField;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.util.Jetty;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

import javax.annotation.PreDestroy;

@Slf4j
@Configuration
public class HttpClientConfig {
    @Value("${rpki.validator.http.proxy.host:#{null}}")
    private String proxyHost;

    @Value("${rpki.validator.http.proxy.port:#{null}}")
    private Integer proxyPort;

    @Value("${rpki.validator.rrdp.trust.all.tls.certificates}")
    private boolean trustAllTlsCertificates;

    private HttpClient httpClientInstance;



    @Bean
    @Scope("singleton")
    public HttpClient client(BuildInformation buildInformation) throws Exception {
        if (trustAllTlsCertificates) {
            log.warn("All TLS certificates are being accepted: HTTPS is effectively disabled. This is **NOT** recommended.");
        }

        final SslContextFactory sslContextFactory = new SslContextFactory.Client(trustAllTlsCertificates);
        httpClientInstance = new HttpClient(sslContextFactory);
        log.info("Trust all TLS certificates: {}, proxy host is {}, proxy port is {}", trustAllTlsCertificates, proxyHost, proxyPort);
        if (proxyHost != null && proxyPort != null) {
            ProxyConfiguration proxyConfig = httpClientInstance.getProxyConfiguration();
            HttpProxy proxy = new HttpProxy(proxyHost, proxyPort);
            proxyConfig.getProxies().add(proxy);
        }
        httpClientInstance.setSocketAddressResolver(new HappyEyeballsResolver(httpClientInstance));
        httpClientInstance.setUserAgentField(new HttpField(HttpHeader.USER_AGENT, String.format("RIPE NCC RPKI Validator/%s", buildInformation.getVersion())));

        httpClientInstance.start();

        return httpClientInstance;
    }

    @PreDestroy
    public void stopHttpClient() throws Exception {
        log.info("Stopping http client");
        httpClientInstance.stop();
    }
}
