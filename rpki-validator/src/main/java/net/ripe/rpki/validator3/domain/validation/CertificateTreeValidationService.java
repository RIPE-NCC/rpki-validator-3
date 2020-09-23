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

import com.google.common.collect.ImmutableSortedSet;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.commons.crypto.CertificateRepositoryObject;
import net.ripe.rpki.commons.crypto.cms.manifest.ManifestCms;
import net.ripe.rpki.commons.crypto.crl.X509Crl;
import net.ripe.rpki.commons.crypto.x509cert.X509ResourceCertificate;
import net.ripe.rpki.commons.util.RepositoryObjectType;
import net.ripe.rpki.commons.validation.ValidationLocation;
import net.ripe.rpki.commons.validation.ValidationResult;
import net.ripe.rpki.commons.validation.ValidationStatus;
import net.ripe.rpki.commons.validation.ValidationString;
import net.ripe.rpki.commons.validation.objectvalidators.CertificateRepositoryObjectValidationContext;
import net.ripe.rpki.validator3.api.util.InstantWithoutNanos;
import net.ripe.rpki.validator3.background.ValidationScheduler;
import net.ripe.rpki.validator3.config.ValidationConfig;
import net.ripe.rpki.validator3.domain.ErrorCodes;
import net.ripe.rpki.validator3.domain.metrics.TrustAnchorMetricsService;
import net.ripe.rpki.validator3.domain.validation.ValidatedRpkiObjects.Accumulator;
import net.ripe.rpki.validator3.domain.validation.ValidatedRpkiObjects.TrustAnchorData;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.stream.Collectors.toList;
import static net.ripe.rpki.commons.validation.ValidationString.VALIDATOR_CRL_FOUND;
import static net.ripe.rpki.commons.validation.ValidationString.VALIDATOR_MANIFEST_CONTAINS_ONE_CRL_ENTRY;
import static net.ripe.rpki.commons.validation.ValidationString.VALIDATOR_MANIFEST_ENTRY_FOUND;
import static net.ripe.rpki.commons.validation.ValidationString.VALIDATOR_MANIFEST_ENTRY_HASH_MATCHES;
import static net.ripe.rpki.commons.validation.ValidationString.VALIDATOR_RPKI_REPOSITORY_PENDING;
import static net.ripe.rpki.commons.validation.ValidationString.VALIDATOR_TRUST_ANCHOR_CERTIFICATE_AVAILABLE;
import static net.ripe.rpki.commons.validation.ValidationString.VALIDATOR_TRUST_ANCHOR_CERTIFICATE_RRDP_NOTIFY_URI_OR_REPOSITORY_URI_PRESENT;
import static net.ripe.rpki.validator3.domain.RpkiObjectUtils.newValidationResult;
import static net.ripe.rpki.validator3.storage.data.RpkiRepository.Type.RRDP;
import static net.ripe.rpki.validator3.storage.data.RpkiRepository.Type.RSYNC;

@Service
@Slf4j
public class CertificateTreeValidationService {
    public final long LONG_DURATION_WARNING_MS = 60_000;

    private final ValidationConfig validationConfig;

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
                                            TrustAnchorMetricsService taMetricsService,
                                            ValidationConfig validationConfig) {
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
        this.validationConfig = validationConfig;

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
        if (maybeTrustAnchor.isPresent()) {
            final TrustAnchor trustAnchor = maybeTrustAnchor.get();
            Bench.mark0("validateTa " + trustAnchor.getName(), () -> validateTa(trustAnchor));
        } else {
            log.error("Couldn't find trust anchor {}", trustAnchorId);
        }
    }

    private void validateTa(TrustAnchor trustAnchor) {
        log.info("Starting tree validation for {}", trustAnchor.getName());
        long begin = System.currentTimeMillis();

        final Map<URI, RpkiRepository> registeredRepositories = new ConcurrentHashMap<>();

        final Ref<TrustAnchor> trustAnchorRef = storage.readTx(tx -> trustAnchors.makeRef(tx, trustAnchor.key()));
        final CertificateTreeValidationRun validationRun = new CertificateTreeValidationRun(trustAnchorRef);

        String trustAnchorLocation = trustAnchor.getLocations().get(0);
        ValidationResult validations = ValidationResult.withLocation(trustAnchorLocation).withoutStoringPassingChecks();

        try {
            X509ResourceCertificate trustAnchorCertificate = trustAnchor.getCertificate();
            validations.rejectIfNull(trustAnchorCertificate, VALIDATOR_TRUST_ANCHOR_CERTIFICATE_AVAILABLE);
            if (trustAnchorCertificate == null) {
                return;
            }

            CertificateRepositoryObjectValidationContext context = new CertificateRepositoryObjectValidationContext(
                    URI.create(trustAnchorLocation),
                    trustAnchorCertificate
            );

            trustAnchorCertificate.validate(trustAnchorLocation, context, null, null, validationConfig.validationOptions(), validations);
            if (validations.hasFailureForCurrentLocation()) {
                return;
            }

            URI locationUri = Optional.ofNullable(trustAnchorCertificate.getRrdpNotifyUri()).orElse(trustAnchorCertificate.getRepositoryUri());
            validations.warnIfNull(locationUri, VALIDATOR_TRUST_ANCHOR_CERTIFICATE_RRDP_NOTIFY_URI_OR_REPOSITORY_URI_PRESENT);
            if (locationUri == null) {
                return;
            }

            TrustAnchorData trustAnchorData = TrustAnchorData.of(trustAnchor.getId(), trustAnchor.getName());
            CertificateAuthorityValidationResult certificateAuthorityValidationResult = validateCertificateAuthority(trustAnchorData, registeredRepositories, context);
            validations.addAll(certificateAuthorityValidationResult.getAllValidationResults());

            Accumulator accumulator = certificateAuthorityValidationResult.getAllValidatedObjects();

            if (accumulator.isEmpty()) {
                if (isValidationRunCompleted(validations)) {
                    log.info("No associated objects, validation run: {}, validation result: {}", validationRun.key(), validations);
                }
            }

            storage.writeTx0(tx -> {
                validationRuns.add(tx, validationRun);
                Long t = Time.timed(() -> accumulator.forEach(key -> validationRuns.associateRpkiObjectKey(tx, validationRun, key)));
                logForDuration("Associated {} objects with the validation run {} in {}ms", accumulator.size(), validationRun.key(), t);

                markTaObjectsReachable(tx, trustAnchorCertificate);

                Long tmr = Time.timed(() -> rpkiObjects.markReachable(tx, accumulator.getKeys()));
                logForDuration("Marked {} objects as reachable in {}ms", accumulator.size(), tmr);

                if (isValidationRunCompleted(validations)) {
                    trustAnchor.markInitialCertificateTreeValidationRunCompleted();
                    trustAnchors.update(tx, trustAnchor);
                    if (!settings.isInitialValidationRunCompleted(tx) && trustAnchors.allInitialCertificateTreeValidationRunsCompleted(tx)) {
                        settings.markInitialValidationRunCompleted(tx);
                        log.info("All trust anchors have completed their initial certificate tree validation run, validator is now ready");
                    }
                }
            });
            if (!accumulator.isEmpty()) {
                validatedRpkiObjects.updateByKey(trustAnchorRef, accumulator);
            }
        } finally {
            validationRun.completeWith(validations);
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
                manifest.get(ManifestCms.class, newValidationResult("ta-manifest.mft"))
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

    private CertificateAuthorityValidationResult validateCertificateAuthority(
            final TrustAnchorData trustAnchor,
            final Map<URI, RpkiRepository> registeredRepositories,
            final CertificateRepositoryObjectValidationContext context
    ) {
        ValidationResult validations = newValidationResult(context.getLocation());
        CertificateAuthorityValidationResult result = CertificateAuthorityValidationResult.of(validations);
        try {
            RpkiRepository rpkiRepository = Bench.mark(trustAnchor.getName(),"registerRepository", () ->
                storage.writeTx(tx -> registerRepository(tx, trustAnchor, registeredRepositories, context)));

            validations.warnIfTrue(rpkiRepository.isPending(), VALIDATOR_RPKI_REPOSITORY_PENDING, rpkiRepository.getLocationUri());
            if (rpkiRepository.isPending()) {
                return result;
            }

            X509ResourceCertificate certificate = context.getCertificate();
            URI manifestUri = certificate.getManifestUri();
            validations.setLocation(new ValidationLocation(manifestUri));

            Optional<RpkiObject> manifestObject = Bench.mark(trustAnchor.getName(), "findLatestMftByAKI", () ->
                storage.readTx(tx -> rpkiObjects.findLatestMftByAKI(tx, certificate.getSubjectKeyIdentifier())));

            if (!manifestObject.isPresent()) {
                if (rpkiRepository.getStatus() == RpkiRepository.Status.FAILED) {
                    validations.error(ValidationString.VALIDATOR_NO_MANIFEST_REPOSITORY_FAILED, rpkiRepository.getLocationUri());
                } else {
                    validations.error(ValidationString.VALIDATOR_NO_LOCAL_MANIFEST_NO_MANIFEST_IN_REPOSITORY, manifestUri.toString(), rpkiRepository.getLocationUri());
                }
            }

            final Optional<ManifestCms> maybeManifest = manifestObject.flatMap(x -> x.get(ManifestCms.class, validations));

            validations.rejectIfTrue(manifestObject.isPresent() &&
                            rpkiRepository.getStatus() == RpkiRepository.Status.FAILED &&
                            maybeManifest.isPresent() &&
                            maybeManifest.get().isPastValidityTime(),
                    ValidationString.VALIDATOR_OLD_LOCAL_MANIFEST_REPOSITORY_FAILED, rpkiRepository.getLocationUri());
            if (validations.hasFailureForCurrentLocation()) {
                return result;
            }

            final ManifestCms manifest = maybeManifest.get();
            List<Map.Entry<String, byte[]>> crlEntries = manifest.getFiles().entrySet().stream()
                    .filter(entry -> RepositoryObjectType.parse(entry.getKey()) == RepositoryObjectType.Crl)
                    .collect(toList());
            validations.rejectIfFalse(crlEntries.size() == 1, VALIDATOR_MANIFEST_CONTAINS_ONE_CRL_ENTRY, String.valueOf(crlEntries.size()));
            if (validations.hasFailureForCurrentLocation()) {
                return result;
            }

            Map.Entry<String, byte[]> crlEntry = crlEntries.get(0);
            URI crlUri = manifestUri.resolve(crlEntry.getKey());

            Optional<RpkiObject> crlObject = storage.readTx(tx -> rpkiObjects.findBySha256(tx, crlEntry.getValue()));
            validations.rejectIfFalse(crlObject.isPresent(), VALIDATOR_CRL_FOUND, crlUri.toASCIIString());
            if (validations.hasFailureForCurrentLocation()) {
                return result;
            }

            validations.setLocation(new ValidationLocation(crlUri));
            final Optional<X509Crl> crl = crlObject.flatMap(x -> x.get(X509Crl.class, validations));
            if (validations.hasFailureForCurrentLocation()) {
                return result;
            }

            final X509Crl x509Crl = crl.get();
            x509Crl.validate(crlUri.toASCIIString(), context, null, validationConfig.validationOptions(), validations);
            if (validations.hasFailureForCurrentLocation()) {
                return result;
            }

            validations.setLocation(new ValidationLocation(manifestUri));
            manifest.validate(manifestUri.toASCIIString(), context, x509Crl, manifest.getCrlUri(), validationConfig.validationOptions(), validations);
            if (validations.hasFailureForCurrentLocation()) {
                return result;
            }

            result.add(trustAnchor, manifestObject.get().key(), manifest, manifestUri);

            CertificateAuthorityValidationResult validatedManifestEntries
                    = validateManifestEntries(trustAnchor, registeredRepositories, context, manifestUri, manifest, crlUri, x509Crl);
            result.addAll(validatedManifestEntries);
        } catch (Exception e) {
            validations.error(ErrorCodes.UNHANDLED_EXCEPTION, e.toString(), ExceptionUtils.getStackTrace(e));
        }
        return result;
    }

    private CertificateAuthorityValidationResult validateManifestEntries(
            TrustAnchorData trustAnchor,
            final Map<URI, RpkiRepository> registeredRepositories,
            CertificateRepositoryObjectValidationContext context,
            URI manifestUri,
            ManifestCms manifest,
            URI crlUri,
            X509Crl x509Crl
    ) {
        CertificateAuthorityValidationResult result = manifest.getFiles().entrySet().parallelStream()
                .map(entry -> validateManifestEntry(trustAnchor, registeredRepositories, context, manifestUri, crlUri, x509Crl, entry))
                .collect(
                        CertificateAuthorityValidationResult::empty,
                        CertificateAuthorityValidationResult::addAll,
                        CertificateAuthorityValidationResult::addAll
                );

        if (validationConfig.isStrictValidation() && result.getValidationResult().hasFailures()) {
            // RFC 6486bis: drop all validated objects from this CA (and its children) since there
            // was at least one validation failure on this manifest.
            return CertificateAuthorityValidationResult.of(result.getValidationResult());
        } else {
            return result;
        }
    }

    private CertificateAuthorityValidationResult validateManifestEntry(
            TrustAnchorData trustAnchor,
            final Map<URI, RpkiRepository> registeredRepositories,
            CertificateRepositoryObjectValidationContext context,
            URI manifestUri,
            URI crlUri,
            X509Crl crl,
            Map.Entry<String, byte[]> entry
    ) {
        Key key = Key.of(entry.getValue());
        URI location = manifestUri.resolve(entry.getKey());
        ValidationResult validations = newValidationResult(location);
        CertificateAuthorityValidationResult result = CertificateAuthorityValidationResult.of(validations);

        Optional<RpkiObject> object = storage.readTx(tx -> rpkiObjects.findBySha256(tx, entry.getValue()));
        validations.rejectIfFalse(object.isPresent(), VALIDATOR_MANIFEST_ENTRY_FOUND, manifestUri.toASCIIString());
        if (validations.hasFailureForCurrentLocation()) {
            return result;
        }

        RpkiObject rpkiObject = object.get();
        boolean hashMatches = Arrays.equals(rpkiObject.getSha256(), entry.getValue());
        validations.rejectIfFalse(hashMatches, VALIDATOR_MANIFEST_ENTRY_HASH_MATCHES, entry.getKey());
        if (validations.hasFailureForCurrentLocation()) {
            return result;
        }

        final Optional<CertificateRepositoryObject> maybeCertificateRepositoryObject = Bench.mark(trustAnchor.getName(),
                "rpkiObject.get", () -> rpkiObject.get(CertificateRepositoryObject.class, validations));
        if (validations.hasFailureForCurrentLocation()) {
            return result;
        }

        CertificateRepositoryObject certificateRepositoryObject = maybeCertificateRepositoryObject.get();
        Bench.mark0(trustAnchor.getName(), "certificateRepositoryObject.validate", () ->
                certificateRepositoryObject.validate(location.toASCIIString(), context, crl, crlUri, validationConfig.validationOptions(), validations)
        );
        if (validations.hasFailureForCurrentLocation()) {
            return result;
        }

        result.add(trustAnchor, key, certificateRepositoryObject, location);

        if (certificateRepositoryObject instanceof X509ResourceCertificate) {
            X509ResourceCertificate resourceCertificate = (X509ResourceCertificate) certificateRepositoryObject;
            if (resourceCertificate.isCa()) {
                CertificateRepositoryObjectValidationContext childContext = context.createChildContext(location, resourceCertificate);
                result.addChild(validateCertificateAuthority(trustAnchor, registeredRepositories, childContext));
            }
        }

        return result;
    }

    private RpkiRepository registerRepository(Tx.Write tx,
                                              TrustAnchorData trustAnchor,
                                              Map<URI, RpkiRepository> registeredRepositories,
                                              CertificateRepositoryObjectValidationContext context) {

        if (!validationConfig.isRsyncOnly() && context.getRpkiNotifyURI() != null) {
            return registeredRepositories.computeIfAbsent(
                    context.getRpkiNotifyURI(),
                    uri -> {
                        final Ref<TrustAnchor> trustAnchorRef = trustAnchors.makeRef(tx, trustAnchor.getId());
                        RpkiRepository r = rpkiRepositories.register(tx, trustAnchorRef, uri.toASCIIString(), RRDP);
                        tx.afterCommit(() -> validationScheduler.addRrdpRpkiRepository(r));
                        return r;
                    });
        }
        return registeredRepositories.computeIfAbsent(
                context.getRepositoryURI(),
                uri -> {
                    final Ref<TrustAnchor> trustAnchorRef = trustAnchors.makeRef(tx, trustAnchor.getId());
                    return rpkiRepositories.register(tx, trustAnchorRef, uri.toASCIIString(), RSYNC);
                });
    }

    @Value(staticConstructor = "of")
    static class CertificateAuthorityValidationResult {
        ValidationResult validationResult;
        Accumulator accumulator = new Accumulator();
        List<CertificateAuthorityValidationResult> children = new ArrayList<>();

        public static CertificateAuthorityValidationResult empty() {
            return CertificateAuthorityValidationResult.of(newValidationResult("<empty>"));
        }

        public void add(TrustAnchorData trustAnchor, Key key, CertificateRepositoryObject object, URI location) {
            this.accumulator.add(trustAnchor, key, object, ImmutableSortedSet.of(location.toASCIIString()));
        }

        public void addAll(CertificateAuthorityValidationResult that) {
            this.validationResult.addAll(that.validationResult);
            this.accumulator.addAll(that.accumulator);
            this.children.addAll(that.children);
        }

        public void addChild(CertificateAuthorityValidationResult result) {
            this.children.add(result);
        }

        public ValidationResult getAllValidationResults() {
            ValidationResult result = newValidationResult(this.validationResult.getCurrentLocation());
            result.addAll(this.validationResult);
            for (CertificateAuthorityValidationResult child : children) {
                result.addAll(child.getAllValidationResults());
            }
            return result;
        }

        public Accumulator getAllValidatedObjects() {
            Accumulator result = new Accumulator();
            result.addAll(this.accumulator);
            for (CertificateAuthorityValidationResult child : children) {
                result.addAll(child.getAllValidatedObjects());
            }
            return result;
        }
    }
}
