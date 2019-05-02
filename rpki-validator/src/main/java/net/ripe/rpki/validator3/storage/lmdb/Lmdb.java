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

import com.google.common.collect.Sets;
import com.google.gson.Gson;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.validator3.storage.Bytes;
import net.ripe.rpki.validator3.storage.data.Key;
import net.ripe.rpki.validator3.storage.encoding.Coder;
import net.ripe.rpki.validator3.storage.encoding.CoderFactory;
import org.apache.commons.lang3.tuple.Pair;
import org.lmdbjava.Dbi;
import org.lmdbjava.DbiFlags;
import org.lmdbjava.Env;
import org.lmdbjava.EnvInfo;
import org.lmdbjava.Stat;
import org.lmdbjava.Txn;

import java.io.Serializable;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.lmdbjava.DbiFlags.MDB_CREATE;

@Slf4j
public abstract class Lmdb {

    private static final String METADATA_MAP_NAME = "meta";

    private Dbi<ByteBuffer> metadata;

    private Gson gson = new Gson();

    protected synchronized Dbi<ByteBuffer> meta() {
        if (metadata == null) {
            metadata = getEnv().openDbi(METADATA_MAP_NAME, MDB_CREATE);
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

    private final Map<String, IxBase<?>> ixMaps = new ConcurrentHashMap<>();

    public <T extends Serializable> IxMap<T> createIxMap(String name,
                                                         Map<String, Function<T, Set<Key>>> indexFunctions,
                                                         Class<T> c) {
        return createIxMap(name, indexFunctions, CoderFactory.makeCoder(c));
    }


    public <T extends Serializable> MultIxMap<T> createMultIxMap(final String name, Coder<T> c) {
        MultIxMap<T> ixMap = new MultIxMap<>(this, name, c);
        ixMaps.put(name, ixMap);
        return ixMap;
    }

    public <T extends Serializable> IxMap<T> createIxMap(final String name,
                                                         final Map<String, Function<T, Set<Key>>> indexFunctions,
                                                         Coder<T> c) {
        IxMap<T> ixMap = new IxMap<>(this, name, c, indexFunctions);
        ixMaps.put(name, ixMap);
        return ixMap;
    }

    public <T extends Serializable> IxMap<T> createSameSizeKeyIxMap(final int keySize,
                                                                    final String name,
                                                                    final Map<String, Function<T, Set<Key>>> indexFunctions,
                                                                    Coder<T> c) {
        SameSizeKeyIxMap<T> ixMap = new SameSizeKeyIxMap<>(keySize, this, name, c, indexFunctions);
        ixMaps.put(name, ixMap);
        return ixMap;
    }

    Dbi<ByteBuffer> createMainMapDb(String name, DbiFlags[] mainDbCreateFlags) {
        final String dbName = name + "-main";
        final Dbi<ByteBuffer> dbi = getEnv().openDbi(dbName, mainDbCreateFlags);

        // don't go into infinite recursion when creating IxMap for meta and do it manually
        final IxMapInfo mapInfo = new IxMapInfo();
        mapInfo.setName(dbName);
        saveDbMeta(mapInfo);
        return dbi;
    }

    private void saveDbMeta(IxMapInfo mapInfo) {
        final Key key = dbMetaKey(mapInfo.getName());
        final ByteBuffer foo = Bytes.toDirectBuffer(gson.toJson(mapInfo).getBytes(UTF_8));
        meta().put(key.toByteBuffer(), foo);
    }

    private Key dbMetaKey(String dbName) {
        return Key.of(dbName + "-key");
    }

    <T extends Serializable> Pair<Map<String, Dbi<ByteBuffer>>, Boolean> createIndexes(
            String name,
            Map<String, Function<T, Set<Key>>> indexFunctions,
            DbiFlags[] indexDbiFlags) {
        final Dbi<ByteBuffer> meta = meta();
        IxMapInfo existingIxMapInfo = readTx(tx -> {
            ByteBuffer bb = meta.get(tx.txn(), dbMetaKey(name).toByteBuffer());
            if (bb == null) {
                return null;
            }
            String json = new String(Bytes.toBytes(bb), UTF_8);
            return gson.fromJson(json, IxMapInfo.class);
        });

        final Map<String, Dbi<ByteBuffer>> indexes = new HashMap<>();
        final Env<ByteBuffer> env = getEnv();
        boolean reindex = false;
        if (existingIxMapInfo != null) {
            final Set<String> existingIndexes = existingIxMapInfo.getIndexes();
            if (existingIndexes != null) {
                if (!existingIndexes.equals(indexFunctions.keySet())) {
                    Sets.difference(existingIndexes, indexFunctions.keySet()).forEach(idx -> {
                        final Dbi<ByteBuffer> idxDbi = env.openDbi(indexDbName(name, idx));
                        try (Txn<ByteBuffer> txn = env.txnWrite()) {
                            idxDbi.drop(txn, true);
                            txn.commit();
                        }
                        idxDbi.close();
                    });
                    existingIxMapInfo.setIndexes(indexFunctions.keySet());
                    saveDbMeta(existingIxMapInfo);
                    reindex = true;
                }
            } else {
                existingIxMapInfo.setIndexes(indexFunctions.keySet());
                saveDbMeta(existingIxMapInfo);
            }
        } else {
            IxMapInfo mapInfo = new IxMapInfo();
            mapInfo.setName(name);
            mapInfo.setIndexes(indexFunctions.keySet());
            saveDbMeta(mapInfo);
        }
        indexFunctions.forEach((n, idxFun) ->
                indexes.put(n, env.openDbi(indexDbName(name, n), indexDbiFlags)));
        return Pair.of(indexes, reindex);
    }

    private String indexDbName(String name, String idx) {
        return name + "-idx-" + idx;
    }

    @Data
    private static class IxMapInfo {
        private String name;
        private Set<String> indexes;
    }

    @Data
    public static class TxInfo {
        private Long txId;
        private Long threadId;
        private List<String> stackTrace;
        private boolean writing;
        private Instant startedAt;

        TxInfo(Tx tx) {
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

    @Data
    @AllArgsConstructor
    public class Stat {
        private org.lmdbjava.Stat lmdbStat;
        private EnvInfo info;
        private List<IxMapStat> ixMapStats;
    }

    @Data
    @AllArgsConstructor
    private class IxMapStat {
        private String name;
        private IxBase.Sizes sizes;
    }

    public Stat getStat() {
        final org.lmdbjava.Stat stat = getEnv().stat();
        final EnvInfo info = getEnv().info();
        return readTx(tx -> {
            final List<IxMapStat> ixMapStats = ixMaps.entrySet().stream()
                    .sorted(Comparator.comparing(Map.Entry::getKey))
                    .map(e -> new IxMapStat(e.getKey(), e.getValue().sizeInfo(tx)))
                    .collect(Collectors.toList());
            return new Stat(stat, info, ixMapStats);
        });
    }
}
