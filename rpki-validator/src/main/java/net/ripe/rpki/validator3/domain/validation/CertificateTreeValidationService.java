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

import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.commons.crypto.CertificateRepositoryObject;
import net.ripe.rpki.commons.crypto.cms.manifest.ManifestCms;
import net.ripe.rpki.commons.crypto.crl.X509Crl;
import net.ripe.rpki.commons.crypto.x509cert.X509ResourceCertificate;
import net.ripe.rpki.commons.util.RepositoryObjectType;
import net.ripe.rpki.commons.validation.ValidationLocation;
import net.ripe.rpki.commons.validation.ValidationOptions;
import net.ripe.rpki.commons.validation.ValidationResult;
import net.ripe.rpki.commons.validation.ValidationStatus;
import net.ripe.rpki.commons.validation.ValidationString;
import net.ripe.rpki.commons.validation.objectvalidators.CertificateRepositoryObjectValidationContext;
import net.ripe.rpki.validator3.api.util.InstantWithoutNanos;
import net.ripe.rpki.validator3.background.ValidationScheduler;
import net.ripe.rpki.validator3.domain.ErrorCodes;
import net.ripe.rpki.validator3.domain.metrics.TrustAnchorMetricsService;
import net.ripe.rpki.validator3.storage.Storage;
import net.ripe.rpki.validator3.storage.Tx;
import net.ripe.rpki.validator3.storage.data.Key;
import net.ripe.rpki.validator3.storage.data.Ref;
import net.ripe.rpki.validator3.storage.data.RpkiObject;
import net.ripe.rpki.validator3.storage.data.RpkiRepository;
import net.ripe.rpki.validator3.storage.data.TrustAnchor;
import net.ripe.rpki.validator3.storage.data.validation.CertificateTreeValidationRun;
import net.ripe.rpki.validator3.storage.stores.RpkiObjects;
import net.ripe.rpki.validator3.storage.stores.RpkiRepositories;
import net.ripe.rpki.validator3.storage.stores.Settings;
import net.ripe.rpki.validator3.storage.stores.TrustAnchors;
import net.ripe.rpki.validator3.storage.stores.ValidationRuns;
import net.ripe.rpki.validator3.util.Bench;
import net.ripe.rpki.validator3.util.Time;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.jooq.lambda.tuple.Tuple2;
import org.jooq.lambda.tuple.Tuple3;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static net.ripe.rpki.commons.validation.ValidationString.VALIDATOR_CRL_FOUND;
import static net.ripe.rpki.commons.validation.ValidationString.VALIDATOR_MANIFEST_CONTAINS_ONE_CRL_ENTRY;
import static net.ripe.rpki.commons.validation.ValidationString.VALIDATOR_MANIFEST_ENTRY_FOUND;
import static net.ripe.rpki.commons.validation.ValidationString.VALIDATOR_MANIFEST_ENTRY_HASH_MATCHES;
import static net.ripe.rpki.commons.validation.ValidationString.VALIDATOR_RPKI_REPOSITORY_PENDING;
import static net.ripe.rpki.commons.validation.ValidationString.VALIDATOR_TRUST_ANCHOR_CERTIFICATE_AVAILABLE;
import static net.ripe.rpki.commons.validation.ValidationString.VALIDATOR_TRUST_ANCHOR_CERTIFICATE_RRDP_NOTIFY_URI_OR_REPOSITORY_URI_PRESENT;
import static net.ripe.rpki.validator3.storage.data.RpkiRepository.Type.RRDP;
import static net.ripe.rpki.validator3.storage.data.RpkiRepository.Type.RSYNC;

@Service
@Slf4j
public class CertificateTreeValidationService {
    public final long LONG_DURATION_WARNING_MS = 60_000;

    private static final ValidationOptions VALIDATION_OPTIONS = new ValidationOptions();

    private final TrustAnchorMetricsService taMetricsService;

    private final RpkiObjects rpkiObjects;
    private final RpkiRepositories rpkiRepositories;
    private final Settings settings;
    private final ValidationScheduler validationScheduler;
    private final ValidationRuns validationRuns;
    private final TrustAnchors trustAnchors;
    private final Storage storage;
    private final ValidatedRpkiObjects validatedRpkiObjects;
    private final TrustAnchorState trustAnchorState;

    @Autowired
    public CertificateTreeValidationService(RpkiObjects rpkiObjects,
                                            RpkiRepositories rpkiRepositories,
                                            Settings settings,
                                            ValidationScheduler validationScheduler,
                                            ValidationRuns validationRuns,
                                            TrustAnchors trustAnchors,
                                            ValidatedRpkiObjects validatedRpkiObjects,
                                            Storage storage,
                                            TrustAnchorState trustAnchorState,
                                            TrustAnchorMetricsService taMetricsService) {
        this.rpkiObjects = rpkiObjects;
        this.rpkiRepositories = rpkiRepositories;
        this.settings = settings;
        this.validationScheduler = validationScheduler;
        this.validationRuns = validationRuns;
        this.trustAnchors = trustAnchors;
        this.validatedRpkiObjects = validatedRpkiObjects;
        this.storage = storage;
        this.taMetricsService = taMetricsService;
        this.trustAnchorState = trustAnchorState;
    }

    /** Log at INFO when below threshold, log at WARN when above */
    private void logForDuration(final String message, Object o1, long delta) {
        if (delta > LONG_DURATION_WARNING_MS) {
            log.warn(String.format("SLOW: %s", message), o1, delta);
        } else {
            log.info(message, o1, delta);
        }
    }

    private void logForDuration(final String message, Object o1, Object o2, long delta) {
        if (delta > LONG_DURATION_WARNING_MS) {
            log.warn(String.format("SLOW: %s", message), o1, o2, delta);
        } else {
            log.info(message, o1, o2, delta);
        }
    }

    public void validate(long trustAnchorId) {
        Optional<TrustAnchor> maybeTrustAnchor = storage.readTx(tx -> trustAnchors.get(tx, Key.of(trustAnchorId)));
        if (!maybeTrustAnchor.isPresent()) {
            log.error("Couldn't find trust anchor {}", trustAnchorId);
            return;
        }
        Bench.mark0("validateTa " + maybeTrustAnchor.get().getName(), () -> validateTa(maybeTrustAnchor.get()));
    }

    private void validateTa(TrustAnchor trustAnchor) {
        log.info("Starting tree validation for {}", trustAnchor);
        long begin = System.currentTimeMillis();

        final Map<URI, RpkiRepository> registeredRepositories = createRegisteredRepositoryMap(trustAnchor);

        final Ref<TrustAnchor> trustAnchorRef = storage.readTx(tx -> trustAnchors.makeRef(tx, trustAnchor.key()));
        final CertificateTreeValidationRun validationRun = new CertificateTreeValidationRun(trustAnchorRef);

        String trustAnchorLocation = trustAnchor.getLocations().get(0);
        ValidationResult validationResult = ValidationResult.withLocation(trustAnchorLocation);

        try {
            X509ResourceCertificate trustAnchorCertificate = trustAnchor.getCertificate();
            validationResult.rejectIfNull(trustAnchorCertificate, VALIDATOR_TRUST_ANCHOR_CERTIFICATE_AVAILABLE);
            if (trustAnchorCertificate == null) {
                return;
            }

            CertificateRepositoryObjectValidationContext context = new CertificateRepositoryObjectValidationContext(
                    URI.create(trustAnchorLocation),
                    trustAnchorCertificate
            );

            trustAnchorCertificate.validate(trustAnchorLocation, context, null, null, VALIDATION_OPTIONS, validationResult);
            if (validationResult.hasFailureForCurrentLocation()) {
                return;
            }

            URI locationUri = Optional.ofNullable(trustAnchorCertificate.getRrdpNotifyUri()).orElse(trustAnchorCertificate.getRepositoryUri());
            validationResult.warnIfNull(locationUri, VALIDATOR_TRUST_ANCHOR_CERTIFICATE_RRDP_NOTIFY_URI_OR_REPOSITORY_URI_PRESENT);
            if (locationUri == null) {
                return;
            }

            final List<Key> rpkiObjectsKeys = Collections.synchronizedList(new ArrayList<>());
            validateCertificateAuthority(trustAnchor, registeredRepositories, context, validationResult, rpkiObjectsKeys);
            if (rpkiObjectsKeys.isEmpty()) {
                if (isValidationRunCompleted(validationResult)) {
                    log.info("No associated objects, validation run: {}, validation result: {}", validationRun.key(), validationResult);
                }
            }
            storage.writeTx0(tx -> {
                validationRuns.add(tx, validationRun);
                Long t = Time.timed(() -> rpkiObjectsKeys.forEach(key -> validationRuns.associateRpkiObjectKey(tx, validationRun, key)));
                logForDuration("Associated {} objects with the validation run {} in {}ms", rpkiObjectsKeys.size(), validationRun.key(), t);

                markTaObjectsReachable(tx, trustAnchorCertificate);

                Long tmr = Time.timed(() -> rpkiObjects.markReachable(tx, rpkiObjectsKeys));
                logForDuration("Marked {} objects as reachable in {}ms", rpkiObjectsKeys.size(), tmr);

                if (isValidationRunCompleted(validationResult)) {
                    trustAnchor.markInitialCertificateTreeValidationRunCompleted();
                    trustAnchors.update(tx, trustAnchor);
                    if (!settings.isInitialValidationRunCompleted(tx) && trustAnchors.allInitialCertificateTreeValidationRunsCompleted(tx)) {
                        settings.markInitialValidationRunCompleted(tx);
                        log.info("All trust anchors have completed their initial certificate tree validation run, validator is now ready");
                    }
                }
            });
            if (!rpkiObjectsKeys.isEmpty()) {
                storage.readTx0(tx -> validatedRpkiObjects.updateByKey(tx, trustAnchorRef, rpkiObjectsKeys));
            }
        } finally {
            validationRun.completeWith(validationResult);
            storage.writeTx0(tx -> validationRuns.update(tx, validationRun));
            trustAnchorState.setValidatedAfterLastRepositoryUpdate(trustAnchor);
            long delta = System.currentTimeMillis() - begin;
            logForDuration("Tree validation {} for {} in {}ms", validationRun.getStatus().toString().toLowerCase(), trustAnchor.getName(), delta);
            taMetricsService.update(trustAnchor, validationRun, delta);
        }
    }

    private void markTaObjectsReachable(Tx.Write tx, X509ResourceCertificate taCertificate) {
        final InstantWithoutNanos now = InstantWithoutNanos.now();
        rpkiObjects.findLatestMftByAKI(tx, taCertificate.getSubjectKeyIdentifier())
            .ifPresent(manifest -> {
                rpkiObjects.markReachable(tx, manifest.key(), now);
                manifest.get(ManifestCms.class, ValidationResult.withLocation("ta-manifest.mft"))
                    .ifPresent(manifestCms ->
                        rpkiObjects.findObjectsInManifest(tx, manifestCms)
                            .forEach((entry, rpkiObject) ->
                                rpkiObjects.markReachable(tx, rpkiObject.key(), now))
                    );
            });
    }

    private boolean isValidationRunCompleted(ValidationResult validationResult) {
        return validationResult.getWarnings().stream()
                .noneMatch(check -> check.getStatus() != ValidationStatus.PASSED && VALIDATOR_RPKI_REPOSITORY_PENDING.equals(check.getKey()));
    }

    private void validateCertificateAuthority(final TrustAnchor trustAnchor,
                                              final Map<URI, RpkiRepository> registeredRepositories,
                                              final CertificateRepositoryObjectValidationContext context,
                                              final ValidationResult validationResult,
                                              final List<Key> validatedObjects) {

        ValidationLocation certificateLocation = validationResult.getCurrentLocation();
        ValidationResult temporary = ValidationResult.withLocation(certificateLocation);
        try {
            RpkiRepository rpkiRepository = Bench.mark(trustAnchor.getName(),"registerRepository", () ->
                storage.writeTx(tx -> registerRepository(tx, trustAnchor, registeredRepositories, context)));

            temporary.warnIfTrue(rpkiRepository.isPending(), VALIDATOR_RPKI_REPOSITORY_PENDING, rpkiRepository.getLocationUri());
            if (rpkiRepository.isPending()) {
                return;
            }

            X509ResourceCertificate certificate = context.getCertificate();
            URI manifestUri = certificate.getManifestUri();
            temporary.setLocation(new ValidationLocation(manifestUri));

            Optional<RpkiObject> manifestObject = Bench.mark(trustAnchor.getName(), "findLatestMftByAKI", () ->
                storage.readTx(tx -> rpkiObjects.findLatestMftByAKI(tx, certificate.getSubjectKeyIdentifier())));

            if (!manifestObject.isPresent()) {
                if (rpkiRepository.getStatus() == RpkiRepository.Status.FAILED) {
                    temporary.error(ValidationString.VALIDATOR_NO_MANIFEST_REPOSITORY_FAILED, rpkiRepository.getLocationUri());
                } else {
                    temporary.error(ValidationString.VALIDATOR_NO_LOCAL_MANIFEST_NO_MANIFEST_IN_REPOSITORY, manifestUri.toString(), rpkiRepository.getLocationUri());
                }
            }

            final Optional<ManifestCms> maybeManifest = manifestObject.flatMap(x -> x.get(ManifestCms.class, temporary));

            temporary.rejectIfTrue(manifestObject.isPresent() &&
                            rpkiRepository.getStatus() == RpkiRepository.Status.FAILED &&
                            maybeManifest.isPresent() &&
                            maybeManifest.get().isPastValidityTime(),
                    ValidationString.VALIDATOR_OLD_LOCAL_MANIFEST_REPOSITORY_FAILED, rpkiRepository.getLocationUri());

            if (temporary.hasFailureForCurrentLocation()) {
                return;
            }

            final ManifestCms manifest = maybeManifest.get();
            List<Map.Entry<String, byte[]>> crlEntries = manifest.getFiles().entrySet().stream()
                    .filter(entry -> RepositoryObjectType.parse(entry.getKey()) == RepositoryObjectType.Crl)
                    .collect(toList());
            temporary.rejectIfFalse(crlEntries.size() == 1, VALIDATOR_MANIFEST_CONTAINS_ONE_CRL_ENTRY, String.valueOf(crlEntries.size()));
            if (temporary.hasFailureForCurrentLocation()) {
                return;
            }

            Map.Entry<String, byte[]> crlEntry = crlEntries.get(0);
            URI crlUri = manifestUri.resolve(crlEntry.getKey());

            Optional<RpkiObject> crlObject = storage.readTx(tx -> rpkiObjects.findBySha256(tx, crlEntry.getValue()));
            temporary.rejectIfFalse(crlObject.isPresent(), VALIDATOR_CRL_FOUND, crlUri.toASCIIString());
            if (temporary.hasFailureForCurrentLocation()) {
                return;
            }

            temporary.setLocation(new ValidationLocation(crlUri));
            final Optional<X509Crl> crl = crlObject.flatMap(x -> x.get(X509Crl.class, temporary));

            if (temporary.hasFailureForCurrentLocation()) {
                return;
            }

            final X509Crl x509Crl = crl.get();
            x509Crl.validate(crlUri.toASCIIString(), context, null, VALIDATION_OPTIONS, temporary);
            if (temporary.hasFailureForCurrentLocation()) {
                return;
            }

            temporary.setLocation(new ValidationLocation(manifestUri));
            manifest.validate(manifestUri.toASCIIString(), context, x509Crl, manifest.getCrlUri(), VALIDATION_OPTIONS, temporary);
            if (temporary.hasFailureForCurrentLocation()) {
                return;
            }
            validatedObjects.add(manifestObject.get().key());

            manifest.getFiles().entrySet()
                .parallelStream()
                .flatMap(entry -> getManifestEntry(manifestUri, entry))
                .flatMap(tuple -> getCertificateRepositoryObjectValidationContext(trustAnchor, context, validatedObjects, crlUri, x509Crl, tuple))
                .map(tuple -> {
                    final ValidationResult vr = tuple.v2();
                    validateCertificateAuthority(trustAnchor, registeredRepositories, tuple.v1(), vr, validatedObjects);
                    return vr;
                })
                .forEachOrdered(temporary::addAll);
        } catch (Exception e) {
            validationResult.error(ErrorCodes.UNHANDLED_EXCEPTION, e.toString(), ExceptionUtils.getStackTrace(e));
        } finally {
            validationResult.addAll(temporary);
        }
    }

    private Stream<Tuple3<URI, RpkiObject, ValidationResult>> getManifestEntry(URI manifestUri,
                                                                               Map.Entry<String, byte[]> entry) {
        URI location = manifestUri.resolve(entry.getKey());
        ValidationResult temporary = ValidationResult.withLocation(new ValidationLocation(location));

        Optional<RpkiObject> object = storage.readTx(tx -> rpkiObjects.findBySha256(tx, entry.getValue()));
        temporary.rejectIfFalse(object.isPresent(), VALIDATOR_MANIFEST_ENTRY_FOUND, manifestUri.toASCIIString());

        Optional<RpkiObject> rpkiObject = object.flatMap(obj -> {
            boolean hashMatches = Arrays.equals(obj.getSha256(), entry.getValue());
            temporary.rejectIfFalse(hashMatches, VALIDATOR_MANIFEST_ENTRY_HASH_MATCHES, entry.getKey());
            return hashMatches ? object : Optional.empty();
        });

        return rpkiObject.map(ro -> Stream.of(new Tuple3<>(location, ro, temporary))).orElse(Stream.empty());
    }

    private Stream<Tuple2<CertificateRepositoryObjectValidationContext, ValidationResult>>
        getCertificateRepositoryObjectValidationContext(TrustAnchor trustAnchor,
                                                        CertificateRepositoryObjectValidationContext context,
                                                        List<Key> validatedObjects,
                                                        URI crlUri,
                                                        X509Crl crl,
                                                        Tuple3<URI, RpkiObject, ValidationResult> e) {
        final URI location = e.v1();
        final RpkiObject rpkiObject = e.v2();
        final ValidationResult temporary = e.v3();

        final Optional<CertificateRepositoryObject> maybeCertificateRepositoryObject = Bench.mark(trustAnchor.getName(),
            "rpkiObject.get", () -> rpkiObject.get(CertificateRepositoryObject.class, temporary));

        if (!temporary.hasFailureForCurrentLocation()) {
            if (maybeCertificateRepositoryObject.isPresent()) {
                CertificateRepositoryObject certificateRepositoryObject = maybeCertificateRepositoryObject.get();
                Bench.mark0(trustAnchor.getName(), "certificateRepositoryObject.validate", () ->
                    certificateRepositoryObject.validate(location.toASCIIString(), context, crl, crlUri, VALIDATION_OPTIONS, temporary));

                if (!temporary.hasFailureForCurrentLocation()) {
                    validatedObjects.add(rpkiObject.key());
                }

                if (certificateRepositoryObject instanceof X509ResourceCertificate
                    && ((X509ResourceCertificate) certificateRepositoryObject).isCa()
                    && !temporary.hasFailureForCurrentLocation()) {

                    final CertificateRepositoryObjectValidationContext childContext = context.createChildContext(location, (X509ResourceCertificate) certificateRepositoryObject);
                    return Stream.of(new Tuple2<>(childContext, temporary));
                }
            }
        }
        return Stream.empty();
    }


    private RpkiRepository registerRepository(Tx.Write tx,
                                              TrustAnchor trustAnchor,
                                              Map<URI, RpkiRepository> registeredRepositories,
                                              CertificateRepositoryObjectValidationContext context) {

        if (context.getRpkiNotifyURI() != null) {
            return registeredRepositories.computeIfAbsent(
                    context.getRpkiNotifyURI(),
                    uri -> {
                        final Ref<TrustAnchor> trustAnchorRef = trustAnchors.makeRef(tx, trustAnchor.key());
                        RpkiRepository r = rpkiRepositories.register(tx, trustAnchorRef, uri.toASCIIString(), RRDP);
                        tx.afterCommit(() -> validationScheduler.addRrdpRpkiRepository(r));
                        return r;
                    });
        }
        return registeredRepositories.computeIfAbsent(
                context.getRepositoryURI(),
                uri -> {
                    final Ref<TrustAnchor> trustAnchorRef = trustAnchors.makeRef(tx, trustAnchor.key());
                    return rpkiRepositories.register(tx, trustAnchorRef, uri.toASCIIString(), RSYNC);
                });
    }

    private Map<URI, RpkiRepository> createRegisteredRepositoryMap(TrustAnchor trustAnchor) {
        final Map<URI, RpkiRepository> registeredRepositories = new ConcurrentHashMap<>();
        long t = Time.timed(() -> storage.readTx(tx -> rpkiRepositories.findByTrustAnchor(tx, trustAnchor.key()))
                .forEach(r -> {
                    if (r.getRrdpNotifyUri() != null) {
                        registeredRepositories.put(URI.create(r.getRrdpNotifyUri()), r);
                    } else
                        registeredRepositories.put(URI.create(r.getLocationUri()), r);
                }));
        logForDuration("Pre-loaded {} repositories in {}ms", registeredRepositories.size(), t);
        return registeredRepositories;
    }

}
