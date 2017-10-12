package net.ripe.rpki.validator3.rrdp;

public class RrdpException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public RrdpException() {
    }

    public RrdpException(String message) {
        super(message);
    }

    public RrdpException(String message, Throwable cause) {
        super(message, cause);
    }
}
