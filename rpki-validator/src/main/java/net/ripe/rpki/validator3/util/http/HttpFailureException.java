package net.ripe.rpki.validator3.util.http;

public class HttpFailureException extends RuntimeException {
    public HttpFailureException(String s) {
        super(s);
    }

    public HttpFailureException(String s, Throwable cause) {
        super(s, cause);
    }
}
