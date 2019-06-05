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

import net.ripe.rpki.validator3.storage.Tx;
import net.ripe.rpki.validator3.storage.data.Key;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;

public interface IxMap<T extends Serializable> extends IxBase<T> {
    Optional<T> get(Key primaryKey);

    Optional<T> get(Tx.Read txn, Key primaryKey);

    List<T> get(Tx.Read txn, Set<Key> primaryKeys);

    Map<Key, T> getByIndex(String indexName, Tx.Read tx, Key indexKey);

    Set<Key> getPkByIndex(String indexName, Tx.Read tx, Key indexKey);

    Map<Key, T> getByIndexLess(String indexName, Tx.Read tx, Key indexKey);

    Map<Key, T> getByIndexGreater(String indexName, Tx.Read tx, Key indexKey);

    Set<Key> getByIndexLessPk(String indexName, Tx.Read tx, Key indexKey);

    Set<Key> getByIndexGreaterPk(String indexName, Tx.Read tx, Key indexKey);

    Map<Key, T> getByIndexMax(String indexName, Tx.Read tx, Predicate<T> p);

    Map<Key, T> getByIndexMin(String indexName, Tx.Read tx, Predicate<T> p);

    Set<Key> getPkByIndexMax(String indexName, Tx.Read tx);

    Set<Key> getPkByIndexMin(String indexName, Tx.Read tx);

    Optional<T> put(Tx.Write tx, Key primaryKey, T value);

    boolean modify(Tx.Write tx, Key primaryKey, Consumer<T> modifyValue);

    void delete(Tx.Write tx, Key primaryKey);

    void onDelete(BiConsumer<Tx.Write, Key> bf);

    void clear(Tx.Write tx);

    LmdbIxBase.Sizes sizeInfo(Tx.Read tx);
}
