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

import com.google.common.collect.Sets;
import com.google.gson.Gson;
import jetbrains.exodus.ArrayByteIterable;
import jetbrains.exodus.ByteIterable;
import jetbrains.exodus.env.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.validator3.storage.*;
import net.ripe.rpki.validator3.storage.data.Key;
import net.ripe.rpki.validator3.storage.encoding.Coder;
import net.ripe.rpki.validator3.storage.encoding.CoderFactory;
import net.ripe.rpki.validator3.storage.Storage;
import org.apache.commons.lang3.tuple.Pair;

import java.io.Serializable;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static jetbrains.exodus.env.StoreConfig.WITHOUT_DUPLICATES;

@Slf4j
public abstract class Xodus implements Storage {

    private static final String METADATA_MAP_NAME = "meta";
    private Gson gson = new Gson();
    private Store metadata;

    protected synchronized Store meta() {
        if (metadata == null) {
            metadata = getEnv().computeInTransaction(txn ->
                    getEnv().openStore(METADATA_MAP_NAME, WITHOUT_DUPLICATES, txn));
        }
        return metadata;
    }

    protected abstract Environment getEnv();

    public <T> T writeTx(Function<Tx.Write, T> f) {
        Environment env = getEnv();
        return env.computeInExclusiveTransaction(txn -> {
            XodusTx.Write tx = XodusTx.fromRWNative(env, txn);
            txs.put(tx.getId(), new TxInfo(tx));
            try {
                T result = f.apply(tx);
                if (tx.getAtCommit() != null) {
                    tx.getAtCommit().forEach(r -> {
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
                txs.remove(tx.getId());
            }
        });
    }

    public void writeTx0(Consumer<Tx.Write> c) {
        writeTx(tx -> {
            c.accept(tx);
            return null;
        });
    }

    public <T> T readTx(Function<Tx.Read, T> f) {
        Environment env = getEnv();
        return env.computeInReadonlyTransaction(txn -> {
            XodusTx.Read tx = XodusTx.fromRONative(env, txn);
            txs.put(tx.getId(), new TxInfo(tx));
            try {
                return f.apply(tx);
            } finally {
                txs.remove(tx.getId());
            }
        });
    }

    public void readTx0(Consumer<Tx.Read> c) {
        readTx(tx -> {
            c.accept(tx);
            return null;
        });
    }

    static void checkEnv(Environment env) {
        if (!env.isOpen()) {
            throw new XodusClosedException();
        }
    }

    public String status() {
        EnvironmentStatistics statistics = (EnvironmentStatistics) getEnv().getStatistics();
        return Arrays.stream(EnvironmentStatistics.Type.values())
                .map(k -> k.name() + ":" + statistics.getStatisticsItem(k).getTotal())
                .collect(Collectors.joining(","));

    }

    @Getter
    private final Map<Long, TxInfo> txs = new ConcurrentHashMap<>();

    private final Map<String, IxBase<?>> ixMaps = new ConcurrentHashMap<>();

    public <T extends Serializable> IxMap<T> createIxMap(String name,
                                                         Map<String, Function<T, Set<Key>>> indexFunctions,
                                                         Class<T> c) {
        return createIxMap(name, indexFunctions, CoderFactory.makeCoder(c));
    }

    public <T extends Serializable> IxMap<T> createIxMap(final String name,
                                                         final Map<String, Function<T, Set<Key>>> indexFunctions,
                                                         Coder<T> c) {
        XodusIxMap ixMap = new XodusIxMap(this, name, c, indexFunctions);
        ixMaps.put(name, ixMap);
        return ixMap;
    }

    @Override
    public <T extends Serializable> MultIxMap<T> createMultIxMap(String name, Coder<T> c) {
        XodusMultIxMap ixMap = new XodusMultIxMap<>(this, name, c);
        ixMaps.put(name, ixMap);
        return ixMap;
    }

    Store createMainMapDb(String name, StoreConfig storeConfig) {
        final String dbName = name + "-main";
        final Store store = getEnv().computeInTransaction(txn ->
                getEnv().openStore(dbName, storeConfig, txn));

        // don't go into infinite recursion when creating IxMap for meta and do it manually
        final IxMapInfo mapInfo = new IxMapInfo();
        mapInfo.setName(dbName);
        saveDbMeta(mapInfo);
        return store;
    }

    private void saveDbMeta(IxMapInfo mapInfo) {
        final Key key = dbMetaKey(mapInfo.getName());
        final ByteIterable foo = new ArrayByteIterable(gson.toJson(mapInfo).getBytes(UTF_8));
        getEnv().executeInTransaction(tx -> meta().put(tx, key.toByteIterable(), foo));
    }

    private Key dbMetaKey(String dbName) {
        return Key.of(dbName + "-key");
    }

    <T extends Serializable> Pair<Map<String, Store>, Boolean> createIndexes(
            String name,
            Map<String, Function<T, Set<Key>>> indexFunctions,
            StoreConfig storeConfigs) {

        final Store meta = meta();
        Xodus.IxMapInfo existingIxMapInfo = getEnv().computeInReadonlyTransaction(txn -> {
            ByteIterable byteIterable = meta.get(txn, dbMetaKey(name).toByteIterable());
            if (byteIterable == null) {
                return null;
            }
            String json = new String(Bytes.toBytes(byteIterable), UTF_8);
            return gson.fromJson(json, Xodus.IxMapInfo.class);
        });

        final Map<String, Store> indexes = new HashMap<>();
        boolean reindex = false;
        if (existingIxMapInfo != null) {
            final Set<String> existingIndexes = existingIxMapInfo.getIndexes();
            if (existingIndexes != null) {
                if (!existingIndexes.equals(indexFunctions.keySet())) {
                    Sets.difference(existingIndexes, indexFunctions.keySet()).forEach(idx -> {
                        getEnv().executeInTransaction(txn -> {
                                    String idxStoreName = idxStoreName(name, idx);
                                    if (getEnv().storeExists(idxStoreName, txn)) {
                                        getEnv().removeStore(idxStoreName, txn);
                                    }
//                                    getEnv().openStore(idxStoreName, USE_EXISTING, txn);
                                }
                        );
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
            Xodus.IxMapInfo mapInfo = new Xodus.IxMapInfo();
            mapInfo.setName(name);
            mapInfo.setIndexes(indexFunctions.keySet());
            saveDbMeta(mapInfo);
        }
        getEnv().executeInTransaction(txn -> {
            indexFunctions.forEach((n, idxFun) -> {
                Store store = getEnv().openStore(idxStoreName(name, n), storeConfigs, txn);
                indexes.put(n, store);
            });
        });

        return Pair.of(indexes, reindex);
    }

    private String idxStoreName(String name, String idx) {
        return name + "-idx-" + idx;
    }


    @Data
    @AllArgsConstructor
    private class IxMapStat {
        private String name;
        private IxBase.Sizes sizes;
    }

    public Stat getStat() {
        return readTx(tx -> {
            final List<IxMapStat> ixMapStats = ixMaps.entrySet().stream()
                    .sorted(Comparator.comparing(Map.Entry::getKey))
                    .map(e -> new IxMapStat(e.getKey(), e.getValue().sizeInfo(tx)))
                    .collect(Collectors.toList());
            return new Stat(ixMapStats);
        });
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

        TxInfo(XodusTx xodusTx) {
            this.txId = xodusTx.getId();
            this.threadId = xodusTx.getThreadId();
            this.stackTrace = Stream.of(Thread.currentThread().getStackTrace())
                    .skip(2)
                    .map(StackTraceElement::toString)
                    .collect(Collectors.toList());
            this.writing = xodusTx instanceof XodusTx.Write;
            this.startedAt = Instant.now();
        }
    }

    @Data
    @AllArgsConstructor
    public class Stat {
        List<IxMapStat> statistics;
    }
}
