package net.ripe.rpki.validator3.util.http;

import lombok.Getter;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.api.Response;

@Getter
public class HttpStatusException extends HttpFailureException {
    private int code;

    public HttpStatusException(Response response, Request request) {
        this(response.getStatus(), String.format("unexpected response status %d for %s", response.getStatus(), request.getURI()));
    }

    public HttpStatusException(int code, String message) {
        super(message);
        this.code = code;
    }
}
