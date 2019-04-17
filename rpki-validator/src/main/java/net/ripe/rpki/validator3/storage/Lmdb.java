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
package net.ripe.rpki.validator3.storage;

import lombok.Data;
import lombok.Getter;
import net.ripe.rpki.validator3.storage.encoding.Coder;
import net.ripe.rpki.validator3.storage.encoding.FSTCoder;
import net.ripe.rpki.validator3.storage.encoding.GsonCoder;
import net.ripe.rpki.validator3.storage.lmdb.Tx;
import org.lmdbjava.Env;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class Lmdb {

    public <T> T writeTx(Function<Tx.Write, T> f) {
        Tx.Write tx = Tx.write(getEnv());
        txs.put(tx.getId(), new TxInfo(tx));
        try {
            final T result = f.apply(tx);
            tx.txn().commit();
            if (tx.getOnCommit() != null) {
                tx.getOnCommit().forEach(r -> {
                    try {
                        r.run();
                    } catch (Exception ignored) {
                        // this is just to keep the loop going, every Runnable
                        // has to take care of exceptions themselves
                    }
                });
            }
            return result;
        } finally {
            tx.close();
            txs.remove(tx.getId());
        }
    }

    public void writeTx0(Consumer<Tx.Write> c) {
        writeTx(tx -> {
            c.accept(tx);
            return null;
        });
    }

    public <T> T readTx(Function<Tx.Read, T> f) {
        Tx.Read tx = Tx.read(getEnv());
        txs.put(tx.getId(), new TxInfo(tx));
        try {
            return f.apply(tx);
        } finally {
            tx.close();
            txs.remove(tx.getId());
        }
    }

    public void readTx0(Consumer<Tx.Read> c) {
        readTx(tx -> {
            c.accept(tx);
            return null;
        });
    }

    public abstract Env<ByteBuffer> getEnv();

    public <T> Coder<T> defaultCoder() {
        return new FSTCoder<>();
    }

    public <T> Coder<T> defaultCoder(Class<T> c) {
        return new GsonCoder<>(c);
//        return new BsonCoder<>(c);
    }


    public String status() {
        return getEnv().stat().toString();
    }

    @Getter
    private final Map<Long, TxInfo> txs = new ConcurrentHashMap<>();

    @Data
    public static class TxInfo {
        private Long txId;
        private Long threadId;
        private List<String> stackTrace;
        private boolean writing;
        private Instant startedAt;

        public TxInfo(Tx tx) {
            this.txId = tx.getId();
            this.threadId = tx.getThreadId();
            this.stackTrace = Stream.of(Thread.currentThread().getStackTrace())
                    .skip(2)
                    .map(StackTraceElement::toString)
                    .collect(Collectors.toList());
            this.writing = tx instanceof Tx.Write;
            this.startedAt = Instant.now();
        }
    }
}
