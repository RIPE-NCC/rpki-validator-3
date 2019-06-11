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
package net.ripe.rpki.validator3.domain.cleanup;

import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.commons.crypto.cms.manifest.ManifestCms;
import net.ripe.rpki.commons.crypto.x509cert.X509ResourceCertificate;
import net.ripe.rpki.commons.validation.ValidationResult;
import net.ripe.rpki.validator3.storage.Tx;
import net.ripe.rpki.validator3.storage.data.Key;
import net.ripe.rpki.validator3.storage.data.RpkiObject;
import net.ripe.rpki.validator3.storage.data.TrustAnchor;
import net.ripe.rpki.validator3.storage.Storage;
import net.ripe.rpki.validator3.storage.stores.RpkiObjects;
import net.ripe.rpki.validator3.storage.stores.TrustAnchors;
import net.ripe.rpki.validator3.util.Time;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class RpkiObjectCleanupService {

    @Autowired
    private TrustAnchors trustAnchors;

    @Autowired
    private RpkiObjects rpkiObjects;

    private final Duration cleanupGraceDuration;

    private final Storage storage;


    public RpkiObjectCleanupService(@Value("${rpki.validator.rpki.object.cleanup.grace.duration}") String cleanupGraceDuration,
                                    Storage storage) {
        this.cleanupGraceDuration = Duration.parse(cleanupGraceDuration);
        log.info("Configured to remove objects older than {}", cleanupGraceDuration);
        this.storage = storage;
    }

    /**
     * Marks all RPKI objects that are reachable from a trust anchor by following the entries in the manifests.
     * Objects that are no longer reachable will be deleted after a configurable grace duration.
     */
    public long cleanupRpkiObjects() throws Exception {
        Instant now = Instant.now();
        final List<TrustAnchor> trustAnchors = storage.readTx(tx -> this.trustAnchors.findAll(tx));
        final Set<Key> markThem = ConcurrentHashMap.newKeySet();
        log.info("Verify starting cleanup ");
        storage.readTx0(tx -> rpkiObjects.verify(tx));
        final Long t0 = Time.timed(() ->
                trustAnchors.stream()
                        .peek(trustAnchor -> log.debug("tracing objects for trust anchor {}", trustAnchor))
                        .parallel()
                        .forEach(trustAnchor ->
                                storage.readTx0(tx -> {
                                    X509ResourceCertificate resourceCertificate = trustAnchor.getCertificate();
                                    if (resourceCertificate != null) {
                                        traceCertificateAuthority(tx, now, resourceCertificate, markThem);
                                    }
                                })));
        log.info("Found {} reachable RPKI objects in {}ms, verifying", markThem.size(), t0);
        storage.readTx0(tx -> rpkiObjects.verify(tx));
        return storage.writeTx(tx -> {
            Long t = Time.timed(() -> markThem.forEach(pk -> rpkiObjects.markReachable(tx, pk, now)));
            log.info("Marked reachable {} RPKI objects in {}ms", markThem.size(), t);
            log.info("Verification before delete");
            rpkiObjects.verify(tx);
            long delCount = deleteUnreachableObjects(tx, now);
            log.info("Verification after delete");
            rpkiObjects.verify(tx);
            return delCount;
        });
    }

    private long deleteUnreachableObjects(Tx.Write tx, Instant now) {
        Instant unreachableSince = now.minus(cleanupGraceDuration);
        final Pair<Long, Long> count = Time.timed(() -> rpkiObjects.deleteUnreachableObjects(tx, unreachableSince));
        log.info("Removed {} RPKI objects that have not been marked reachable since {}, took {}ms", count.getLeft(), unreachableSince, count.getRight());
        return count.getLeft();
    }

    private void traceCertificateAuthority(Tx.Read tx, Instant now, X509ResourceCertificate resourceCertificate,
                                           Set<Key> markThem) {
        if (resourceCertificate == null || resourceCertificate.getManifestUri() == null) {
            return;
        }

        rpkiObjects.findLatestMftByAKI(tx, resourceCertificate.getSubjectKeyIdentifier())
                .ifPresent(manifest -> markAndTraceObject(tx, now, "manifest.mft", manifest, markThem));
    }

    private void markAndTraceObject(Tx.Read tx, Instant now, String name, RpkiObject rpkiObject, Set<Key> markThem) {
        markThem.add(rpkiObject.key());
        switch (rpkiObject.getType()) {
            case MFT:
                traceManifest(tx, now, name, rpkiObject, markThem);
                break;
            case CER:
                traceCaCertificate(tx, now, name, rpkiObject, markThem);
                break;
            default:
                break;
        }
    }

    private void traceManifest(Tx.Read tx, Instant now, String name, RpkiObject manifest, Set<Key> markThem) {
        rpkiObjects.findCertificateRepositoryObject(tx,
                manifest.key(), ManifestCms.class, ValidationResult.withLocation(name))
                .ifPresent(manifestCms ->
                        rpkiObjects.findObjectsInManifest(tx, manifestCms)
                                .forEach((entry, rpkiObject) ->
                                        markAndTraceObject(tx, now, entry, rpkiObject, markThem)));
    }

    private void traceCaCertificate(Tx.Read tx, Instant now, String name, RpkiObject caCertificate, Set<Key> markThem) {
        rpkiObjects.findCertificateRepositoryObject(tx, caCertificate.key(),
                X509ResourceCertificate.class, ValidationResult.withLocation(name))
                .ifPresent(certificate -> {
                    if (certificate.isCa() && certificate.getManifestUri() != null) {
                        traceCertificateAuthority(tx, now, certificate, markThem);
                    }
                });
    }

}
