package net.ripe.rpki.validator3.storage.lmdb;

public class LmdbClosedException extends RuntimeException {
    public LmdbClosedException() {
        super("The database environment is closed and cannot be used anymore. " +
                "This exception is harmless and can be safely ignored.");
    }
}
