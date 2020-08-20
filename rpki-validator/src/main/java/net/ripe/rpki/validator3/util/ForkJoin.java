package net.ripe.rpki.validator3.util;

import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

public class ForkJoin {
    /**
     * Indicates that supplier is potentially blocking so {@link ForkJoinPool} can spawn additional threads if needed.
     *
     * @param supplier potentially blocking operation
     * @param <T>      type of result
     * @return result of Supplier#get
     * @see ForkJoinPool#managedBlock
     */
    public static <T> T blocking(Supplier<T> supplier) {
        AtomicReference<T> result = new AtomicReference<>();
        try {
            ForkJoinPool.managedBlock(new ForkJoinPool.ManagedBlocker() {
                @Override
                public boolean block() {
                    result.set(supplier.get());
                    return true;
                }

                @Override
                public boolean isReleasable() {
                    return false;
                }
            });
        } catch (InterruptedException e) {
            // We're not throwing an InterruptedException, and neither is ForkJoinPool#managedBlock,
            // so this shouldn't happen.
            throw new RuntimeException(e);
        }
        return result.get();
    }
}
