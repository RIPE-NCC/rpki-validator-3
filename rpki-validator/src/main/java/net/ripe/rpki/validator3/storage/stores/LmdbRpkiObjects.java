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
package net.ripe.rpki.validator3.storage.stores;

import com.google.common.collect.ImmutableMap;
import net.ripe.rpki.commons.crypto.CertificateRepositoryObject;
import net.ripe.rpki.commons.crypto.cms.manifest.ManifestCms;
import net.ripe.rpki.commons.validation.ValidationResult;
import net.ripe.rpki.validator3.storage.FSTCoder;
import net.ripe.rpki.validator3.storage.Lmdb;
import net.ripe.rpki.validator3.storage.data.RpkiObject;
import net.ripe.rpki.validator3.storage.lmdb.Key;
import net.ripe.rpki.validator3.storage.lmdb.IxMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

@Component
public class LmdbRpkiObjects implements RpkiObjectsStore {

    public static final String RPKI_OBJECTS = "rpki-objects";
    public static final String BY_AKI_INDEX = "by-aki";
    public static final String BY_LAST_REACHABLE_INDEX = "by-last-reachable";

    private final IxMap<RpkiObject> ixMap;

    private Key lasMarkedReachableKey(RpkiObject rpkiObject) {
        return Key.of(rpkiObject.getLastMarkedReachableAt().toEpochMilli());
    }

    @Autowired
    public LmdbRpkiObjects(Lmdb lmdb) {
        this.ixMap = new IxMap<>(
                lmdb.getEnv(),
                RPKI_OBJECTS,
                new FSTCoder<>(),
                ImmutableMap.of(
                        BY_AKI_INDEX, rpkiObject -> Key.of(rpkiObject.getAuthorityKeyIdentifier()),
                        BY_LAST_REACHABLE_INDEX, this::lasMarkedReachableKey)
        );
    }

    @Override
    public void add(RpkiObject o) {
        ixMap.put(Key.of(o.getSha256()), o);
    }

    @Override
    public void remove(RpkiObject o) {
        ixMap.delete(Key.of(o.getSha256()));
    }

    @Override
    public <T extends CertificateRepositoryObject> Optional<T> findCertificateRepositoryObject(
            byte[] sha256, Class<T> clazz, ValidationResult validationResult) {
        return findBySha256(sha256).flatMap(o -> o.get(clazz, validationResult));
    }

    @Override
    public Optional<RpkiObject> findBySha256(byte[] sha256) {
        return ixMap.get(ixMap.readTx(), Key.of(sha256));
    }

    @Override
    public Map<String, RpkiObject> findObjectsInManifest(ManifestCms manifestCms) {
        return null;
    }

    @Override
    public List<RpkiObject> all() {
        return ixMap.values(ixMap.readTx());
    }

    @Override
    public Optional<RpkiObject> findLatestByTypeAndAuthorityKeyIdentifier(RpkiObject.Type type, byte[] authorityKeyIdentifier) {
        return ixMap.getByIndex(BY_AKI_INDEX, ixMap.readTx(), Key.of(authorityKeyIdentifier))
                .stream().findFirst();
    }

    @Override
    public long deleteUnreachableObjects(Instant unreachableSince) {
        return 0;
    }

    @Override
    public void clear() {
        ixMap.clear();
    }

    @Override
    public Stream<byte[]> streamObjects(RpkiObject.Type type) {
        return null;
    }
}
