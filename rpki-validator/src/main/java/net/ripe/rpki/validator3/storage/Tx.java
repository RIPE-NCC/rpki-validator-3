package net.ripe.rpki.validator3.storage;

public class Tx {

    public interface Read {
        Object txn();

        void close();
    }

    public interface Write extends Read {
        void abort();
    }

}

