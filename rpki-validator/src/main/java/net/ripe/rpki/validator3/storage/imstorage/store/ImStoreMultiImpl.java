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

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import net.ripe.rpki.validator3.storage.data.Key;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;

public class ImStoreMultiImpl<T> implements ImStore<T> {

    private final Multimap<Key, T> multimap;

    @Override
    public void put(Key primaryKey, T value) {
        multimap.put(primaryKey, value);
    }

    public ImStoreMultiImpl() {
        this.multimap = HashMultimap.create();
    }

    @Override
    public boolean containsKey(Key key) {
        return multimap.containsKey(key);
    }

    @Override
    public void delete(Key key, T value) {
        multimap.remove(key, value);
    }

    @Override
    public Set<Key> keys() {
        return multimap.keySet();
    }

    @Override
    public List<T> values() {
        return Collections.unmodifiableList(new ArrayList<>(multimap.values()));
    }

    @Override
    public Map<Key, T> getMap() {
        throw new RuntimeException("Shouldn't force multimap into normal map, values will be multiple");
    }

    @Override
    public void clear() {
        multimap.clear();
    }

    @Override
    public int size() {
        return 0;
    }

    @Override
    public T get(Key primaryKey) {
        // Why would you want to get an element from multimap?
        return multimap.get(primaryKey).iterator().next();
    }


    public List<T> getByKeys(Collection<Key> idxKeys) {
        return idxKeys.stream().flatMap(k->multimap.get(k).stream()).collect(Collectors.toList());
    }

    public Collection<T> getPrimaryKeys(Key indexKey) {
       return multimap.get(indexKey);
    }


    public SortedSet<Key> sortedKeys(){
        TreeSet<Key> ordered = new TreeSet<>(Comparator.comparing(Key::toByteIterable));
        ordered.addAll(keys());
        return ordered;
    }

    public SortedSet<Key> sortedKeysRev(){
        TreeSet<Key> ordered = new TreeSet<>(Comparator.comparing(Key::toByteIterable).reversed());
        ordered.addAll(keys());
        return ordered;
    }

    public boolean exists(Key key, T value){
        return multimap.containsEntry(key, value);
    }

    public void delete(Key key){
        multimap.removeAll(key);
    }

    @Override
    public Collection<Map.Entry<Key, T>> entries() {
        return multimap.entries();
    }
}
