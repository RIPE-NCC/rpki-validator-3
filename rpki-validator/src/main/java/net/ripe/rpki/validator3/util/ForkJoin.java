/**
 * The BSD License
 *
 * Copyright (c) 2010-2018 RIPE NCC
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *   - Redistributions of source code must retain the above copyright notice,
 *     this list of conditions and the following disclaimer.
 *   - Redistributions in binary form must reproduce the above copyright notice,
 *     this list of conditions and the following disclaimer in the documentation
 *     and/or other materials provided with the distribution.
 *   - Neither the name of the RIPE NCC nor the names of its contributors may be
 *     used to endorse or promote products derived from this software without
 *     specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package net.ripe.rpki.validator3.util;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

@Slf4j
public class ForkJoin {

    private static final Semaphore maximumManagedBlockers;

    static {
        int maximumSpares = Integer.parseInt(System.getProperty("java.util.concurrent.ForkJoinPool.common.maximumSpares", "256"));
        int max = Math.min(2 * ForkJoinPool.getCommonPoolParallelism(), maximumSpares / 4);
        log.info("maximum concurrent blocking threads for common fork-join pool is {}", max);
        maximumManagedBlockers = new Semaphore(max);
    }

    /**
     * Indicates that supplier is potentially blocking so {@link ForkJoinPool} can spawn additional threads if needed.
     *
     * The maximum number of threads marked "blocking" is bounded by the {@see maximumManagedBlockers} semaphore to
     * avoid filling up the spare threads of the fork-join pool and causing
     * {@link java.util.concurrent.RejectedExecutionException}s.
     *
     * Furthermore, this code should only be called when using the common fork-join pool as it does not have a
     * semaphore for other fork-join pools.
     *
     * @param supplier potentially blocking operation
     * @param <T>      type of result
     * @return result of Supplier#get
     * @see ForkJoinPool#managedBlock
     */
    public static <T> T blocking(Supplier<T> supplier) {
        if (!maximumManagedBlockers.tryAcquire()) {
            return supplier.get();
        }

        try {
            AtomicReference<T> result = new AtomicReference<>();
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
            return result.get();
        } catch (InterruptedException e) {
            // We're not throwing an InterruptedException, and neither is ForkJoinPool#managedBlock,
            // so this shouldn't happen.
            throw new RuntimeException(e);
        } finally {
            maximumManagedBlockers.release();
        }
    }
}
