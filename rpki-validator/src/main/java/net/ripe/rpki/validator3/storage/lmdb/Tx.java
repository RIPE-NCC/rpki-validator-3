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

import org.lmdbjava.Env;
import org.lmdbjava.Txn;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Keep only one LMDB transaction per thread. Also have type-level
 * distinction between read-only and read-write transactions.
 *
 * @param <T>
 */
public abstract class Tx<T> implements AutoCloseable {

    protected final Env<T> env;
    private final ThreadLocal<Txn<T>> localWriteTx = ThreadLocal.withInitial(this::makeTxn);

    protected abstract Txn<T> makeTxn();

    private Tx(Env<T> env) {
        this.env = env;
    }

    public static <R> Read<R> read(Env<R> e) {
        return new Read<>(e);
    }

    public static <R> Write<R> write(Env<R> e) {
        return new Write<>(e);
    }

    public Txn<T> txn() {
        return localWriteTx.get();
    }

    public static <R, T> R with(Write<T> wtx, Function<Write<T>, R> f) {
        try (Txn<T> txn = wtx.txn()) {
            R r = f.apply(wtx);
            txn.commit();
            return r;
        }
    }

    public static <T> void use(Write<T> wtx, Consumer<Write<T>> c) {
        try (Txn<T> txn = wtx.txn()) {
            c.accept(wtx);
            txn.commit();
        }
    }

    public static class Write<T> extends Read<T> {
        Write(Env<T> e) {
            super(e);
        }

        @Override
        protected Txn<T> makeTxn() {
            return env.txnWrite();
        }
    }

    public static class Read<T> extends Tx<T> {
        @Override
        protected Txn<T> makeTxn() {
            return env.txnRead();
        }

        Read(Env<T> e) {
            super(e);
        }
    }

    @Override
    public void close() {
        txn().close();
    }
}
