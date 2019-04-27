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

import fj.P;
import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.validator3.storage.data.Key;
import net.ripe.rpki.validator3.storage.encoding.Coder;
import net.ripe.rpki.validator3.storage.encoding.CoderFactory;
import net.ripe.rpki.validator3.storage.lmdb.IxMap;
import net.ripe.rpki.validator3.storage.lmdb.MultIxMap;
import net.ripe.rpki.validator3.storage.lmdb.SameSizeKeyIxMap;
import net.ripe.rpki.validator3.storage.lmdb.Tx;
import org.lmdbjava.Env;

import java.io.Serializable;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
public abstract class Lmdb {

    private IxMap<HashSet<String>> metadata;

    protected synchronized IxMap<HashSet<String>> meta() {
        if (metadata == null) {
            final Coder<HashSet<String>> objectCoder = CoderFactory.makeCoder((Class<HashSet<String>>) new HashSet<String>().getClass());
            metadata = new IxMap<>(getEnv(), "meta", objectCoder, Collections.emptyMap());
        }
        return metadata;
    }

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

    public String status() {
        return getEnv().stat().toString();
    }

    @Getter
    private final Map<Long, TxInfo> txs = new ConcurrentHashMap<>();

    public <T extends Serializable> IxMap<T> createIxMap(String name,
                                                                  Map<String, Function<T, Set<Key>>> indexFunctions,
                                                                  Class<T> c) {
        return createIxMap(name, indexFunctions, CoderFactory.makeCoder(c));
    }



    public <T extends Serializable> MultIxMap<T> createMultIxMap(final String name, Coder<T> c) {
        return new MultIxMap<>(getEnv(), name, c);
    }

    public <T extends Serializable> IxMap<T> createIxMap(final String name,
                                                         final Map<String, Function<T, Set<Key>>> indexFunctions,
                                                         Coder<T> c) {
        final IxMap<T> ixMap = new IxMap<>(getEnv(), name, c, indexFunctions);
        reindexIfNeeded(name, indexFunctions, ixMap);
        return ixMap;
    }

    public <T extends Serializable> IxMap<T> createSameSizeIxMap(final int keySize,
                                                                 final String name,
                                                                 final Map<String, Function<T, Set<Key>>> indexFunctions,
                                                                 Coder<T> c) {
        final IxMap<T> ixMap = new SameSizeKeyIxMap<>(keySize, getEnv(), name, c, indexFunctions);
        reindexIfNeeded(name, indexFunctions, ixMap);
        return ixMap;
    }

    private <T extends Serializable> void reindexIfNeeded(String name, Map<String, Function<T, Set<Key>>> indexFunctions, IxMap<T> ixMap) {
        final Set<String> indexes = indexFunctions.keySet();
        final IxMap<HashSet<String>> meta = meta();
        final Key key = Key.of(name + ":indexes");
        HashSet<String> xxx = readTx(tx -> {
            final Optional<HashSet<String>> existingIndexes = meta.get(tx, key);
            if (existingIndexes.isPresent() && !existingIndexes.get().equals(indexes)) {
                log.warn("For the map {} there is a difference between indexes in DB {} and the definition in the code {}",
                        name, existingIndexes.get(), indexes);
                return existingIndexes.get();
            }
            return null;
        });

        if (xxx != null) {
            xxx.forEach(idxName -> {

            });
            ixMap.reindex(xxx);
        }
        writeTx0(tx -> meta.put(tx, key, new HashSet<>(indexes)));
    }

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
