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
package net.ripe.rpki.validator3.storage.xodus;

import jetbrains.exodus.env.Environment;
import jetbrains.exodus.env.Transaction;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.validator3.storage.Tx;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * This is to have type-level distinction between
 * read-only and read-write transactions.
 *
 */
@Slf4j
public abstract class XodusTx implements AutoCloseable {

    protected final Environment env;
    protected final Transaction txn;
    @Getter
    private final long threadId;
    @Getter
    private final long id;
    private boolean aborted = false;

    private static AtomicLong idseq = new AtomicLong(1);

    private XodusTx(Environment env) {
        threadId = Thread.currentThread().getId();
        this.env = env;
        id = idseq.getAndIncrement();
        txn = makeTxn();
    }

    private XodusTx(Environment env, Transaction txn) {
        threadId = Thread.currentThread().getId();
        this.env = env;
        id = idseq.getAndIncrement();
        this.txn = txn;
    }

    static Read fromRONative(Environment env, Transaction txn) {
        return new Read(env, txn);
    }

    static Write fromRWNative(Environment env, Transaction txn) {
        return new Write(env, txn);
    }

    protected abstract Transaction makeTxn();

    public static Read read(Environment e) {
        return new Read(e);
    }

    public static Write write(Environment e) {
        return new Write(e);
    }

    public Transaction txn() {
        verifyState();
        return txn;
    }

    private void verifyState() {
        if (aborted) {
            throw new RuntimeException("Transaction " + txn.getHighAddress() + " was aborted.");
        }
        if (Thread.currentThread().getId() != threadId) {
            throw new RuntimeException("This transaction was created in another " +
                    "thread and cannot be used in the thread " + Thread.currentThread());
        }
        checkEnv();
    }

    void checkEnv() {
        Xodus.checkEnv(env);
    }

    public void abort() {
        checkEnv();
        txn.abort();
        aborted = true;
    }

    public static class Write extends Read implements Tx.Write {
        Write(Environment e) {
            super(e);
        }

        Write(Environment e, Transaction txn) {
            super(e, txn);
        }

        @Override
        protected Transaction makeTxn() {
            checkEnv();
            return env.beginExclusiveTransaction();
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

    public static class Read extends XodusTx implements Tx.Read {
        Read(Environment e) {
            super(e);
        }

        Read(Environment e, Transaction txn) {
            super(e, txn);
        }

        @Override
        protected Transaction makeTxn() {
            checkEnv();
            return env.beginReadonlyTransaction();
        }
    }

    @Override
    public void close() {
    }
}
