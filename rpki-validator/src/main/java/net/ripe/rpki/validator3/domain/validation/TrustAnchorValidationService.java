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
package net.ripe.rpki.validator3.domain.validation;

import com.google.common.base.Joiner;
import com.google.common.collect.Sets;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.commons.crypto.CertificateRepositoryObject;
import net.ripe.rpki.commons.crypto.util.CertificateRepositoryObjectFactory;
import net.ripe.rpki.commons.crypto.x509cert.X509CertificateUtil;
import net.ripe.rpki.commons.crypto.x509cert.X509ResourceCertificate;
import net.ripe.rpki.commons.rsync.CommandExecutionException;
import net.ripe.rpki.commons.validation.ValidationLocation;
import net.ripe.rpki.commons.validation.ValidationResult;
import net.ripe.rpki.commons.validation.ValidationString;
import net.ripe.rpki.validator3.background.ValidationScheduler;
import net.ripe.rpki.validator3.domain.ErrorCodes;
import net.ripe.rpki.validator3.domain.metrics.TrustAnchorMetricsService;
import net.ripe.rpki.validator3.domain.retrieval.TrustAnchorRetrievalService;
import net.ripe.rpki.validator3.storage.Storage;
import net.ripe.rpki.validator3.storage.data.Key;
import net.ripe.rpki.validator3.storage.data.Ref;
import net.ripe.rpki.validator3.storage.data.RpkiObject;
import net.ripe.rpki.validator3.storage.data.RpkiRepository;
import net.ripe.rpki.validator3.storage.data.TrustAnchor;
import net.ripe.rpki.validator3.storage.data.validation.TrustAnchorValidationRun;
import net.ripe.rpki.validator3.storage.data.validation.ValidationCheck;
import net.ripe.rpki.validator3.storage.stores.RpkiRepositories;
import net.ripe.rpki.validator3.storage.stores.TrustAnchors;
import net.ripe.rpki.validator3.storage.stores.ValidationRuns;
import org.jooq.lambda.tuple.Tuple2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static net.ripe.rpki.validator3.domain.RpkiObjectUtils.newValidationResult;

@Service
@Slf4j
public class TrustAnchorValidationService {
    private final TrustAnchors trustAnchors;
    private final RpkiRepositories rpkiRepositories;
    private final ValidationRuns validationRuns;
    private final ValidationScheduler validationScheduler;
    private final RpkiRepositoryValidationService repositoryValidationService;
    private final Storage storage;

    private final TrustAnchorMetricsService taMetricsService;
    private final TrustAnchorRetrievalService trustAnchorRetrievalService;

    private Set<Key> validatedAtLeastOnce = Collections.newSetFromMap(new ConcurrentHashMap<>());


    @Autowired
    public TrustAnchorValidationService(
        TrustAnchors trustAnchors,
        RpkiRepositories rpkiRepositories,
        ValidationRuns validationRuns,
        ValidationScheduler validationScheduler,
        RpkiRepositoryValidationService repositoryValidationService,
        Storage storage,
        TrustAnchorMetricsService trustAnchorMetricsService,
        TrustAnchorRetrievalService trustAnchorRetrievalService) {
        this.trustAnchors = trustAnchors;
        this.rpkiRepositories = rpkiRepositories;
        this.validationRuns = validationRuns;
        this.validationScheduler = validationScheduler;
        this.repositoryValidationService = repositoryValidationService;
        this.storage = storage;
        this.taMetricsService = trustAnchorMetricsService;
        this.trustAnchorRetrievalService = trustAnchorRetrievalService;
    }

    public void validate(long trustAnchorId) {
        Optional<TrustAnchor> maybeTrustAnchor = storage.readTx(tx -> trustAnchors.get(tx, Key.of(trustAnchorId)));
        if (!maybeTrustAnchor.isPresent()) {
            log.error("Trust anchor {} doesn't exist.", trustAnchorId);
            return;
        }

        TrustAnchor trustAnchor = maybeTrustAnchor.get();
        log.info("trust anchor {} located at {} with subject public key info {}", trustAnchor.getName(), trustAnchor.getLocations(), trustAnchor.getSubjectPublicKeyInfo());

        TrustAnchorValidationRun validationRun = storage.readTx(tx -> {
            final Ref<TrustAnchor> trustAnchorRef = trustAnchors.makeRef(tx, Key.of(trustAnchorId));
            return new TrustAnchorValidationRun(trustAnchorRef, trustAnchor.getLocations().get(0));
        });

        final ValidationLocation trustAnchorValidationLocation = new ValidationLocation(validationRun.getTrustAnchorCertificateURI());
        ValidationResult validationResult = newValidationResult(trustAnchorValidationLocation);

        boolean updatedTrustAnchor = false;
        try {
            final Optional<Tuple2<URI, byte[]>> maybeTrustAnchorCertificate = fetchPreferredTrustAnchorCertificate(trustAnchor, validationResult);

            if (maybeTrustAnchorCertificate.isPresent()) {
                final Tuple2<URI, byte[]> res = maybeTrustAnchorCertificate.get();
                updatedTrustAnchor = readTrustAnchorFromLocation(res.v2, trustAnchor, res.v1, validationResult);
            } else {
                validationResult.error(
                        ErrorCodes.TRUST_ANCHOR_FETCH,
                        "any location",
                        String.format("None of the locations (%s) could be loaded.", Joiner.on(", ").join(trustAnchor.getLocations())));
                validationRun.setFailed();
            }

            if (validationResult.hasFailures()) {
                log.warn("Validation result for the TA {} has failures: {}", trustAnchor.getName(),
                        validationResult.getFailuresForAllLocations());
            }

            if (trustAnchor.getRsyncPrefetchUri() != null) {
                storage.writeTx0(tx -> {
                    final Ref<TrustAnchor> trustAnchorRef = trustAnchors.makeRef(tx, trustAnchor.key());
                    rpkiRepositories.register(tx, trustAnchorRef,
                            trustAnchor.getRsyncPrefetchUri(), RpkiRepository.Type.RSYNC_PREFETCH);
                });
            }

            validationRun.completeWith(validationResult);
            if (!validatedAtLeastOnce.contains(trustAnchor.getId()) || updatedTrustAnchor) {
                if (updatedTrustAnchor) {
                    storage.writeTx0(tx -> trustAnchors.update(tx, trustAnchor));
                }
                final Set<TrustAnchor> affectedTrustAnchors = Sets.newHashSet(trustAnchor);
                if (trustAnchor.getRsyncPrefetchUri() != null) {
                    storage.readTx(tx ->
                            rpkiRepositories.findByURI(tx, trustAnchor.getRsyncPrefetchUri()))
                            .ifPresent(r ->
                                    affectedTrustAnchors.addAll(repositoryValidationService.prefetchRepository(r)));
                }
                affectedTrustAnchors.forEach(validationScheduler::triggerCertificateTreeValidation);
            }
        } catch (CommandExecutionException | IOException e) {
            log.error("validation run for trust anchor {} failed", trustAnchor, e);
            validationRun.addCheck(new ValidationCheck(validationRun.getTrustAnchorCertificateURI(), ValidationCheck.Status.ERROR, ErrorCodes.UNHANDLED_EXCEPTION, e.toString()));
            validationRun.setFailed();
        } finally {
            validatedAtLeastOnce.add(trustAnchor.getId());
            storage.writeTx0(tx -> validationRuns.add(tx, validationRun));
        }
    }

    /**
     * Attempt to fetch a trust anchor (TA) certificate for this TrustAnchor by attempting to retrieve the TA certificates
     * in order of preference. When successful, return the URI of the certificate as well as it's content.
     *
     * @param trustAnchor to load certificate for
     * @param validationResult of current validation
     * @return (URI of certificate, content of certificate)
     */
    public Optional<Tuple2<URI, byte[]>> fetchPreferredTrustAnchorCertificate(TrustAnchor trustAnchor, ValidationResult validationResult) {
        final ValidationLocation initialLocation = validationResult.getCurrentLocation();
        final List<URI> rankedTrustAnchorLocations = trustAnchor.getLocationsByPreference();

        // Try all locations until one was successful
        for (URI currentLocation : rankedTrustAnchorLocations) {
            // Switch validation location
            validationResult.setLocation(new ValidationLocation(currentLocation));

            byte[] trustAnchorCertificate = trustAnchorRetrievalService.fetchTrustAnchorCertificate(currentLocation, validationResult);

            if (trustAnchorCertificate != null) {
                return Optional.of(new Tuple2<>(currentLocation, trustAnchorCertificate));
            }
        }

        validationResult.setLocation(initialLocation);
        return Optional.empty();
    }

    private boolean readTrustAnchorFromLocation(byte[] trustAnchorCertificate, TrustAnchor trustAnchor, URI trustAnchorCertificateURI, ValidationResult validationResult) throws IOException {
        long trustAnchorCertificateSize = trustAnchorCertificate.length;

        if (trustAnchorCertificateSize < RpkiObject.MIN_SIZE) {
            validationResult.error(ErrorCodes.REPOSITORY_OBJECT_MINIMUM_SIZE, trustAnchorCertificateURI.toASCIIString(), String.valueOf(trustAnchorCertificateSize), String.valueOf(RpkiObject.MIN_SIZE));
        } else if (trustAnchorCertificateSize > RpkiObject.MAX_SIZE) {
            validationResult.error(ErrorCodes.REPOSITORY_OBJECT_MAXIMUM_SIZE, trustAnchorCertificateURI.toASCIIString(), String.valueOf(trustAnchorCertificateSize), String.valueOf(RpkiObject.MAX_SIZE));
        } else {
            final X509ResourceCertificate parsedCertificate = parseCertificate(trustAnchor, trustAnchorCertificate, trustAnchorCertificateURI, validationResult);

            if (!validationResult.hasFailureForCurrentLocation()) {
                // validate(..) is called multiple times for the same trust anchor certificate (e.g. when the
                // application restarts).
                int comparedSerial = trustAnchor.getCertificate() == null ?
                        1 : parsedCertificate.getSerialNumber().compareTo(trustAnchor.getCertificate().getSerialNumber());
                validationResult.warnIfTrue(comparedSerial < 0, ValidationString.VALIDATOR_REPOSITORY_OBJECT_IS_OLDER_THAN_PREVIOUS_OBJECT, trustAnchorCertificateURI.toASCIIString());
                if (comparedSerial != 0) {
                    log.info("Setting certificate {} for the TA {}", trustAnchorCertificateURI, trustAnchor.getName());
                    trustAnchor.setCertificate(parsedCertificate);
                    return true;
                }
            }
        }
        return false;
    }

    private X509ResourceCertificate parseCertificate(TrustAnchor trustAnchor, byte[] certificateData, URI trustAnchorCertificateURI, ValidationResult validationResult) throws IOException {
        // We provide the certificate uri because it can be one of multiple and the rsync prefetch
        // uri can not be used since it can be null.
        assert trustAnchor.getLocationsByPreference().contains(trustAnchorCertificateURI);

        CertificateRepositoryObject trustAnchorCertificate = CertificateRepositoryObjectFactory.createCertificateRepositoryObject(certificateData, validationResult);
        validationResult.rejectIfFalse(trustAnchorCertificate instanceof X509ResourceCertificate, ErrorCodes.REPOSITORY_OBJECT_IS_TRUST_ANCHOR_CERTIFICATE, trustAnchorCertificateURI.toASCIIString());
        if (validationResult.hasFailureForCurrentLocation()) {
            return null;
        }

        X509ResourceCertificate certificate = (X509ResourceCertificate) trustAnchorCertificate;

        String encodedSubjectPublicKeyInfo = X509CertificateUtil.getEncodedSubjectPublicKeyInfo(certificate.getCertificate());
        validationResult.rejectIfFalse(encodedSubjectPublicKeyInfo.equals(trustAnchor.getSubjectPublicKeyInfo()), "trust.anchor.subject.key.matches.locator");

        boolean signatureValid;
        try {
            certificate.getCertificate().verify(certificate.getPublicKey());
            signatureValid = true;
        } catch (GeneralSecurityException e) {
            signatureValid = false;
        }

        validationResult.rejectIfFalse(signatureValid, ErrorCodes.TRUST_ANCHOR_SIGNATURE, trustAnchorCertificateURI.toASCIIString(), trustAnchor.getSubjectPublicKeyInfo());

        return certificate;
    }
}
