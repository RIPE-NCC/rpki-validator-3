package net.ripe.rpki.validator3.domain.validation;

public class StrictValidationException extends RuntimeException {
    public StrictValidationException(String message) {
        super(message);
    }
}
