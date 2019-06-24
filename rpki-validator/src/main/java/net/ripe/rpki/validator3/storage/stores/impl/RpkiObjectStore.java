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
package net.ripe.rpki.validator3.storage.stores.impl;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.primitives.UnsignedBytes;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.commons.crypto.CertificateRepositoryObject;
import net.ripe.rpki.commons.crypto.cms.manifest.ManifestCms;
import net.ripe.rpki.commons.validation.ValidationResult;
import net.ripe.rpki.validator3.storage.IxMap;
import net.ripe.rpki.validator3.storage.MultIxMap;
import net.ripe.rpki.validator3.storage.Storage;
import net.ripe.rpki.validator3.storage.Tx;
import net.ripe.rpki.validator3.storage.data.Key;
import net.ripe.rpki.validator3.storage.data.RpkiObject;
import net.ripe.rpki.validator3.storage.encoding.CoderFactory;
import net.ripe.rpki.validator3.storage.stores.GenericStoreImpl;
import net.ripe.rpki.validator3.storage.stores.RpkiObjects;
import net.ripe.rpki.validator3.util.Bench;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
@Slf4j
public class RpkiObjectStore extends GenericStoreImpl<RpkiObject> implements RpkiObjects {

    private static final String RPKI_OBJECTS = "rpki-objects";
    private static final String REACHABLE_MAP = "rpki-objects-reachable";
    private static final String LOCATION_MAP = "rpki-objects-location";
    private static final String BY_AKI_MFT_INDEX = "by-aki-mft";
    private static final String BY_TYPE_INDEX = "by-type";

    private final IxMap<RpkiObject> ixMap;
    private final IxMap<Long> reachableMap;
    private final MultIxMap<String> locationMap;
    private final Storage storage;

    private Set<Key> akiMftKey(RpkiObject rpkiObject) {
        byte[] authorityKeyIdentifier = rpkiObject.getAuthorityKeyIdentifier();
        return (rpkiObject.getType() == RpkiObject.Type.MFT && authorityKeyIdentifier != null) ?
                Key.keys(Key.of(authorityKeyIdentifier)) :
                Collections.emptySet();
    }

    private Set<Key> typeKey(RpkiObject rpkiObject) {
        return Key.keys(Key.of(rpkiObject.getType().toString()));
    }

    @Autowired
    public RpkiObjectStore(Storage storage) {
        this.storage = storage;
        this.ixMap = storage.createIxMap(
                RPKI_OBJECTS,
                ImmutableMap.of(
                        BY_AKI_MFT_INDEX, this::akiMftKey,
                        BY_TYPE_INDEX, this::typeKey),
                CoderFactory.makeCoder(RpkiObject.class));

        this.reachableMap = storage.createIxMap(REACHABLE_MAP, ImmutableMap.of(), CoderFactory.longCoder());
        this.locationMap = storage.createMultIxMap(LOCATION_MAP, CoderFactory.stringCoder());

        ixMap.onDelete((tx, k) -> {
            reachableMap.delete(tx, k);
            locationMap.delete(tx, k);
        });
    }

    @Override
    public void put(Tx.Write tx, RpkiObject o) {
        ixMap.put(tx, o.key(), o);
        // mark every object as reachable at the moment of inserting, otherwise
        // we will keep the objects that have never been reached forever
        markReachable(tx, o.key(), o.getCreatedAt());
    }

    @Override
    public void put(Tx.Write tx, RpkiObject o, String location) {
        put(tx, o);
        addLocation(tx, o.key(), location);
    }

    @Override
    public void delete(Tx.Write tx, RpkiObject o) {
        ixMap.delete(tx, o.key());
    }

    @Override
    public void markReachable(Tx.Write tx, Key pk, Instant i) {
        reachableMap.put(tx, pk, i.toEpochMilli());
    }

    @Override
    public void addLocation(Tx.Write tx, Key pk, String location) {
        if(!locationMap.exists(tx, pk, location)) {
            locationMap.put(tx, pk, location);
        }
    }

    @Override
    public SortedSet<String> getLocations(Tx.Read tx, Key pk) {
        return new TreeSet<>(locationMap.get(tx, pk));
    }

    @Override
    public void deleteLocation(Tx.Write tx, Key key, String uri) {
        locationMap.delete(tx, key, uri);
    }

    @Override
    public <T extends CertificateRepositoryObject> Optional<T> findCertificateRepositoryObject(
            Tx.Read tx, Key sha256, Class<T> clazz, ValidationResult validationResult) {
        return get(tx, sha256).flatMap(o -> o.get(clazz, validationResult));
    }

    @Override
    public Optional<RpkiObject> get(Tx.Read tx, Key key) {
        return ixMap.get(tx, key);
    }

    @Override
    public Optional<RpkiObject> findBySha256(Tx.Read tx, byte[] sha256) {
        return get(tx, Key.of(sha256));
    }

    @Override
    public Optional<RpkiObject> findLatestMftByAKI(Tx.Read tx, byte[] authorityKeyIdentifier) {
        return Bench.mark("findLatestMftByAKI", () -> ixMap.getByIndex(BY_AKI_MFT_INDEX, tx, Key.of(authorityKeyIdentifier))
            .values()
            .stream()
            .max(Comparator
                .comparing(RpkiObject::getSerialNumber)
                .thenComparing(RpkiObject::getSigningTime)));
    }

    @Override
    public long deleteUnreachableObjects(Instant unreachableSince) {
        final List<Key> toDelete = new ArrayList<>();
        storage.readTx0(tx ->
                reachableMap.forEach(tx, (k, bytes) -> {
                    if (reachableMap.toValue(bytes) < unreachableSince.toEpochMilli()) {
                        // do not do ixMap.delete(tx, k) right here -- it will cause onDelete
                        // trigger and deletion of pair at the current cursor position, which is
                        // potentially a problem
                        toDelete.add(k);
                    }
                }));
        Lists.partition(toDelete, 1000).forEach(chunk -> {
            storage.writeTx0(tx ->
                    chunk.forEach(pk -> ixMap.delete(tx, pk)));
        });
        return (long) toDelete.size();
    }

    @Override
    public Map<String, RpkiObject> findObjectsInManifest(Tx.Read tx, ManifestCms manifestCms) {
        final SortedMap<byte[], String> hashes = new TreeMap<>(UnsignedBytes.lexicographicalComparator());
        manifestCms.getFiles().forEach((name, hash) -> hashes.put(hash, name));
        return hashes.keySet().stream()
                .map(sha256 -> findBySha256(tx, sha256))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toMap(
                        x -> hashes.get(x.getSha256()),
                        x -> x
                ));
    }

    @Override
    public Stream<byte[]> streamObjects(Tx.Read tx, RpkiObject.Type type) {
        final List<byte[]> objectBytes = new ArrayList<>();
        getPkByType(tx, type).forEach(pk ->
                ixMap.get(tx, pk).ifPresent(ro ->
                        objectBytes.add(ro.getEncoded())));
        return objectBytes.stream();
    }

    @Override
    public Set<Key> getPkByType(Tx.Read tx, RpkiObject.Type type) {
        return ixMap.getPkByIndex(BY_TYPE_INDEX, tx, Key.of(type.toString()));
    }

    @Override
    public void markReachable(Tx.Write tx, List<Key> rpkiObjectsKeys) {
        final Instant now = Instant.now();
        rpkiObjectsKeys.forEach(pk -> markReachable(tx, pk, now));
    }

    @Override
    protected IxMap<RpkiObject> ixMap() {
        return ixMap;
    }
}
