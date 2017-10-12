package net.ripe.rpki.validator3.rrdp;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.http2.client.HTTP2Client;
import org.eclipse.jetty.http2.client.http.HttpClientTransportOverHTTP2;
import org.eclipse.jetty.util.ssl.SslContextFactory;

public class RrdpClient {

    private HttpClient httpClient;

    public RrdpClient() throws Exception {
        final HTTP2Client http2Client = new HTTP2Client();
        final SslContextFactory sslContextFactory = new SslContextFactory();
        httpClient = new HttpClient(new HttpClientTransportOverHTTP2(http2Client), sslContextFactory);
        httpClient.start();
    }

    public String getSnapshot(final String uri) {
        try {
            final ContentResponse response = httpClient.GET(uri);
            return response.getContentAsString();
        } catch (Exception e) {
            throw new RrdpException("Couldn't fetch snapshot " + uri, e);
        }
    }


}
