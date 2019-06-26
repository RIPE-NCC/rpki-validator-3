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
package net.ripe.rpki.validator3.storage.imstorage;

import net.ripe.rpki.validator3.storage.MultIxMap;
import net.ripe.rpki.validator3.storage.Tx;
import net.ripe.rpki.validator3.storage.data.Key;
import net.ripe.rpki.validator3.storage.encoding.Coder;
import net.ripe.rpki.validator3.storage.imstorage.store.ImStoreMultiImpl;
import org.apache.commons.lang3.tuple.Pair;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class ImMultiIxMap<T extends Serializable> extends ImIxBase<T> implements MultIxMap<T> {

    private final String name;
    private final ImStoreMultiImpl<T> mStore;
    private final Coder<T> coder;

    public ImMultiIxMap(String name, Coder<T> coder) {
        super(name, coder);
        this.name = name;
        this.coder = coder;
        this.mStore = (ImStoreMultiImpl<T>)getMainStore();

    }

    @Override
    public List<T> get(Tx.Read tx, Key primaryKey) {
        return new ArrayList<>(mStore.getPrimaryKeys(primaryKey));
    }

    @Override
    public int count(Tx.Read tx, Key idxKey) {
        return mStore.getPrimaryKeys(idxKey).size();
    }

    @Override
    public void put(Tx.Write tx, Key primaryKey, T value) {
        mStore.put(primaryKey, value);
    }

    @Override
    public void delete(Tx.Write tx, Key key) {
        mStore.delete(key);
    }

    @Override
    public void delete(Tx.Write tx, Key primaryKey, T value) {
        mStore.delete(primaryKey, value);
    }

    @Override
    public void deleteBatch(Tx.Write tx, List<Pair<Key, T>> toDelete) {
        toDelete.forEach(kp -> delete(tx, kp.getKey(), kp.getValue()));
    }

    @Override
    public boolean exists(Tx.Read tx, Key pk, T value) {
        return mStore.exists(pk, value);
    }

    @Override
    protected boolean withDuplicates() {
        return true;
    }
}
