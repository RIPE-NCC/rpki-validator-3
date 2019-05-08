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
package net.ripe.rpki.validator3.storage.lmdb;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.lmdbjava.Env;
import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * This is to have type-level distinction between
 * read-only and read-write transactions.
 *
 */
@Slf4j
public abstract class Tx implements AutoCloseable {

    protected final Env<ByteBuffer> env;
    private final Txn<ByteBuffer> txn;
    @Getter
    private final long threadId;
    @Getter
    private final long id;
    private boolean aborted = false;

    private static AtomicLong idseq = new AtomicLong(1);

    private Tx(Env<ByteBuffer> env) {
        threadId = Thread.currentThread().getId();
        this.env = env;
        id = idseq.getAndIncrement();
        txn = makeTxn();
    }

    protected abstract Txn<ByteBuffer> makeTxn();

    public static Read read(Env<ByteBuffer> e) {
        return new Read(e);
    }

    public static Write write(Env<ByteBuffer> e) {
        return new Write(e);
    }

    Txn<ByteBuffer> txn() {
        verifyState();
        return txn;
    }

    private void verifyState() {
        if (aborted) {
            throw new RuntimeException("Transaction " + txn.getId() + " was aborted.");
        }
        if (Thread.currentThread().getId() != threadId) {
            throw new RuntimeException("This transaction was created in another " +
                    "thread and cannot be used in the thread " + Thread.currentThread());
        }
        checkEnv();
    }

    void checkEnv() {
        Lmdb.checkEnv(env);
    }

    public void abort() {
        checkEnv();
        txn.abort();
        aborted = true;
    }

    public static class Write extends Read {
        Write(Env<ByteBuffer> e) {
            super(e);
        }

        @Override
        protected Txn<ByteBuffer> makeTxn() {
            checkEnv();
            return env.txnWrite();
        }

        @Getter
        private List<Runnable> afterCommit = null;

        public synchronized void afterCommit(Runnable r) {
            if (afterCommit == null) {
                afterCommit = new ArrayList<>();
            }
            afterCommit.add(r);
        }
    }

    public static class Read extends Tx {
        Read(Env<ByteBuffer> e) {
            super(e);
        }

        @Override
        protected Txn<ByteBuffer> makeTxn() {
            checkEnv();
            return env.txnRead();
        }
    }

    @Override
    public void close() {
        txn().close();
    }
}
