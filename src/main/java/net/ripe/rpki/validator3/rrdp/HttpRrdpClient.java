package net.ripe.rpki.validator3.rrdp;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.Response;
import org.eclipse.jetty.client.util.InputStreamResponseListener;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@Component
public class HttpRrdpClient implements RrdpClient {

    private HttpClient httpClient;

    public HttpRrdpClient() throws Exception {
        final SslContextFactory sslContextFactory = new SslContextFactory();
        // TODO @mpuzanov find out why using HttpClientTransportOverHTTP2 makes GET request hang
        httpClient = new HttpClient(sslContextFactory);
        httpClient.start();
    }

    @Override
    public <T> T readStream(final String uri, Function<InputStream, T> reader) {
        InputStreamResponseListener listener = new InputStreamResponseListener();
        httpClient.newRequest(uri).send(listener);

        final Response response;
        try {
            response = listener.get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new RrdpException("Couldn't read response stream", e);
        }

        if (response.getStatus() == 200) {
            try (InputStream inputStream = listener.getInputStream()) {
                return reader.apply(inputStream);
            } catch (IOException e) {
                response.abort(new RrdpException("Couldn't read response stream", e));
            }
        }
        response.abort(new RrdpException("Response status: " + response.getStatus()));
        return null;
    }

}
