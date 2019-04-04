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
import net.ripe.rpki.validator3.storage.Lmdb;
import net.ripe.rpki.validator3.storage.data.RpkiObject;
import net.ripe.rpki.validator3.storage.data.TrustAnchor;
import net.ripe.rpki.validator3.storage.lmdb.Tx;
import net.ripe.rpki.validator3.storage.stores.RpkiObjectStore;
import net.ripe.rpki.validator3.storage.stores.TrustAnchorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
@Slf4j
public class RpkiObjectCleanupService {

    @Autowired
    private TrustAnchorStore trustAnchors;

    @Autowired
    private RpkiObjectStore rpkiObjects;

    private final Duration cleanupGraceDuration;

    private final Lmdb lmdb;


    public RpkiObjectCleanupService(@Value("${rpki.validator.rpki.object.cleanup.grace.duration}") String cleanupGraceDuration,
                                    Lmdb lmdb) {
        this.cleanupGraceDuration = Duration.parse(cleanupGraceDuration);
        this.lmdb = lmdb;
    }

    /**
     * Marks all RPKI objects that are reachable from a trust anchor by following the entries in the manifests.
     * Objects that are no longer reachable will be deleted after a configurable grace duration.
     */
    public long cleanupRpkiObjects() throws Exception {
        Instant now = Instant.now();
        final List<TrustAnchor> trustAnchors = Tx.rwith(lmdb.readTx(), tx -> this.trustAnchors.findAll(tx));
        for (TrustAnchor trustAnchor : trustAnchors) {
            Tx.use(lmdb.writeTx(), tx -> {
                log.debug("tracing objects for trust anchor {}", trustAnchor);
                X509ResourceCertificate resourceCertificate = trustAnchor.getCertificate();
                if (resourceCertificate != null) {
                    traceCertificateAuthority(tx, now, resourceCertificate);
                }
            });
        }

        // do it in async as well to guarantee that it's executed after all the markings
        return async.submit(() ->
                Tx.with(lmdb.writeTx(), tx -> deleteUnreachableObjects(tx, now))).get();
    }

    private long deleteUnreachableObjects(Tx.Write tx, Instant now) {
        Instant unreachableSince = now.minus(cleanupGraceDuration);
        long count = rpkiObjects.deleteUnreachableObjects(tx, unreachableSince);
        log.info("Removed {} RPKI objects that have not been marked reachable since {}", count, unreachableSince);
        return count;
    }

    private void traceCertificateAuthority(Tx.Read tx, Instant now, X509ResourceCertificate resourceCertificate) {
        if (resourceCertificate == null || resourceCertificate.getManifestUri() == null) {
            return;
        }

        rpkiObjects.findLatestByTypeAndAuthorityKeyIdentifier(tx,
                RpkiObject.Type.MFT, resourceCertificate.getSubjectKeyIdentifier())
                .ifPresent(manifest -> markAndTraceObject(tx, now, "manifest.mft", manifest));
    }

    private final ExecutorService async = Executors.newSingleThreadExecutor();

    private void markAndTraceObject(Tx.Read tx, Instant now, String name, RpkiObject rpkiObject) {
        // Compare object instance identity to see if we've already visited
        // the `rpkiObject` in the current run.
        if (now == rpkiObject.getLastMarkedReachableAt()) {
            log.debug("Object already marked, skipping {}", rpkiObject);
        }

        async.submit(() -> {
            rpkiObject.markReachable(now);
            Tx.use(lmdb.writeTx(), tx1 -> rpkiObjects.put(tx1, rpkiObject));
        });

        switch (rpkiObject.getType()) {
            case MFT:
                traceManifest(tx, now, name, rpkiObject);
                break;
            case CER:
                traceCaCertificate(tx, now, name, rpkiObject);
                break;
            default:
                break;
        }
    }

    private void traceManifest(Tx.Read tx, Instant now, String name, RpkiObject manifest) {
        rpkiObjects.findCertificateRepositoryObject(tx,
                manifest.getId(), ManifestCms.class, ValidationResult.withLocation(name))
                .ifPresent(manifestCms ->
                        rpkiObjects.findObjectsInManifest(tx, manifestCms)
                                .forEach((entry, rpkiObject) ->
                                        markAndTraceObject(tx, now, entry, rpkiObject)));
    }

    private void traceCaCertificate(Tx.Read tx, Instant now, String name, RpkiObject caCertificate) {
        rpkiObjects.findCertificateRepositoryObject(tx, caCertificate.getId(),
                X509ResourceCertificate.class, ValidationResult.withLocation(name))
                .ifPresent(certificate -> {
                    if (certificate.isCa() && certificate.getManifestUri() != null) {
                        traceCertificateAuthority(tx, now, certificate);
                    }
                });
    }

}
