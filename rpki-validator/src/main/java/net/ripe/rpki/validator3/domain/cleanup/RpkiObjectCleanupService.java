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
import net.ripe.rpki.validator3.domain.RpkiObject;
import net.ripe.rpki.validator3.domain.RpkiObjects;
import net.ripe.rpki.validator3.domain.TrustAnchor;
import net.ripe.rpki.validator3.domain.TrustAnchors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import javax.persistence.EntityManager;
import javax.persistence.FlushModeType;
import javax.persistence.LockModeType;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Service
@Slf4j
public class RpkiObjectCleanupService {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TrustAnchors trustAnchors;

    @Autowired
    private RpkiObjects rpkiObjects;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private final Duration cleanupGraceDuration;

    public RpkiObjectCleanupService(@Value("${rpki.validator.rpki.object.cleanup.grace.duration}") String cleanupGraceDuration) {
        this.cleanupGraceDuration = Duration.parse(cleanupGraceDuration);
    }

    /**
     * Marks all RPKI objects that are reachable from a trust anchor by following the entries in the manifests.
     * Objects that are no longer reachable will be deleted after a configurable grace duration.
     */
    @Scheduled(initialDelay = 60_000, fixedDelayString = "${rpki.validator.rpki.object.cleanup.interval.ms}")
    public long cleanupRpkiObjects() {
        Instant now = Instant.now();
        for (TrustAnchor trustAnchor : trustAnchors.findAll()) {
            transactionTemplate.execute((status) -> {
                entityManager.setFlushMode(FlushModeType.COMMIT);

                log.debug("tracing objects for trust anchor {}", trustAnchor);

                X509ResourceCertificate resourceCertificate = trustAnchor.getCertificate();
                if (resourceCertificate != null) {
                    traceCertificateAuthority(now, resourceCertificate);
                }

                return null;
            });
        }

        return deleteUnreachableObjects(now);
    }

    private long deleteUnreachableObjects(Instant now) {
        return transactionTemplate.execute((status) -> {
            entityManager.flush();

            Instant unreachableSince = now.minus(cleanupGraceDuration);
            long count = rpkiObjects.deleteUnreachableObjects(unreachableSince);
            log.info("Removed {} RPKI objects that have not been marked reachable since {}", count, unreachableSince);
            return count;
        });
    }

    private void traceCertificateAuthority(Instant now, X509ResourceCertificate resourceCertificate) {
        if (resourceCertificate == null || resourceCertificate.getManifestUri() == null) {
            return;
        }

        Optional<RpkiObject> maybeManifest = rpkiObjects.findLatestByTypeAndAuthorityKeyIdentifier(
            RpkiObject.Type.MFT,
            resourceCertificate.getSubjectKeyIdentifier()
        );
        maybeManifest.ifPresent(manifest -> {
            markAndTraceObject(now, "manifest.mft", manifest);
        });
    }

    private void markAndTraceObject(Instant now, String name, RpkiObject rpkiObject) {
        // Compare object instance identity to see if we've already visited
        // the `rpkiObject` in the current run.
        if (now == rpkiObject.getLastMarkedReachableAt()) {
            log.debug("object already marked, skipping {}", rpkiObject);
        }

        rpkiObject.markReachable(now);
        switch (rpkiObject.getType()) {
            case MFT:
                traceManifest(now, name, rpkiObject);
                break;
            case CER:
                traceCaCertificate(now, name, rpkiObject);
                break;
            default:
                break;
        }
    }

    private void traceManifest(Instant now, String name, RpkiObject manifest) {
        this.rpkiObjects.findCertificateRepositoryObject(manifest.getId(), ManifestCms.class, ValidationResult.withLocation(name)).ifPresent(manifestCms -> {
            rpkiObjects.findObjectsInManifest(manifestCms, LockModeType.PESSIMISTIC_WRITE).forEach((entry, rpkiObject) -> {
                markAndTraceObject(now, entry, rpkiObject);
            });
        });
    }

    private void traceCaCertificate(Instant now, String name, RpkiObject caCertificate) {
        this.rpkiObjects.findCertificateRepositoryObject(caCertificate.getId(), X509ResourceCertificate.class, ValidationResult.withLocation(name)).ifPresent(certificate -> {
            if (certificate.isCa() && certificate.getManifestUri() != null) {
                traceCertificateAuthority(now, certificate);
            }
        });
    }

}
