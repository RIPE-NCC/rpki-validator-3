package net.ripe.rpki.validator3.util.http;

public class NotModifiedException extends HttpStatusException {
    public NotModifiedException(String uri) {
        super(302, String.format("HTTP 302 NOT MODIFIED for %s", uri));
    }
}
