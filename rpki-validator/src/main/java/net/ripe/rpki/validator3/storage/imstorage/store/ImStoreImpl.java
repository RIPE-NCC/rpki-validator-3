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
package net.ripe.rpki.validator3.storage.imstorage.store;

import net.ripe.rpki.validator3.storage.data.Key;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

public class ImStoreImpl<T> implements ImStore<T> {

    private final Map<Key, T> map;

    public ImStoreImpl() {
        this.map = new TreeMap<>(Comparator.comparing(Key::toByteIterable));
    }

    @Override
    public Collection<Map.Entry<Key,T>> entries() {
        return map.entrySet();
    }

    @Override
    public void delete(Key oik, T value) {
        map.remove(oik);
    }

    @Override
    public void put(Key primaryKey, T value) {
        map.put(primaryKey, value);
    }

    @Override
    public boolean containsKey(Key key) {
        return map.containsKey(key);
    }

    @Override
    public Set<Key> keys() {
        return map.keySet();
    }

    @Override
    public List<T> values() {
        return Collections.unmodifiableList(new ArrayList<>(map.values()));
    }

    @Override
    public Map<Key, T> getMap() {
        return map;
    }

    @Override
    public void clear() {
        map.clear();
    }

    @Override
    public int size() {
        return map.size();
    }

    @Override
    public T get(Key primaryKey) {
        return map.get(primaryKey);
    }

    public List<T> getByKeys(Collection<Key> idxKeys) {
        return idxKeys.stream().map(k->map.get(k)).collect(Collectors.toList());
    }
}
