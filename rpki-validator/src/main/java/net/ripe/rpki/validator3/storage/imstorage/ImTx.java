package net.ripe.rpki.validator3.storage.imstorage;

import lombok.Getter;
import net.ripe.rpki.validator3.storage.Tx;

import java.util.ArrayList;
import java.util.List;

public abstract class ImTx implements AutoCloseable  {


    public static class Write extends ImTx.Read implements Tx.Write {
        @Override
        public void abort() {

        }

        @Getter
        private List<Runnable> atCommit = null;

        public synchronized void afterCommit(Runnable r) {
            if (atCommit == null) {
                atCommit = new ArrayList<>();
            }
            atCommit.add(r);
        }
    }

    public static class Read extends ImTx implements Tx.Read {

        @Override
        public Object txn() {
            return null;
        }
    }

    @Override
    public void close(){

    }

    public static Tx.Read readTx(){
        return new Read();
    }
}
