package net.ripe.rpki.validator3.config;

import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.validator3.util.HappyEyeballsResolver;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.HttpProxy;
import org.eclipse.jetty.client.ProxyConfiguration;
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
    public HttpClient client() throws Exception {
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

        httpClientInstance.start();

        return httpClientInstance;
    }

    @PreDestroy
    public void stopHttpClient() throws Exception {
        log.info("Stopping http client");
        httpClientInstance.stop();
    }
}
