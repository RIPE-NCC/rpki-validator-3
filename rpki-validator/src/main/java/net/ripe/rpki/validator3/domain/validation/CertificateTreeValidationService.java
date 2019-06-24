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

import com.google.common.base.Objects;
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
import net.ripe.rpki.validator3.background.ValidationScheduler;
import net.ripe.rpki.validator3.domain.ErrorCodes;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

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
    private static final ValidationOptions VALIDATION_OPTIONS = new ValidationOptions();

    private final RpkiObjects rpkiObjects;
    private final RpkiRepositories rpkiRepositories;
    private final Settings settings;
    private final ValidationScheduler validationScheduler;
    private final ValidationRuns validationRuns;
    private final TrustAnchors trustAnchors;
    private final Storage storage;
    private final ValidatedRpkiObjects validatedRpkiObjects;

    @Autowired
    public CertificateTreeValidationService(RpkiObjects rpkiObjects,
                                            RpkiRepositories rpkiRepositories,
                                            Settings settings,
                                            ValidationScheduler validationScheduler,
                                            ValidationRuns validationRuns,
                                            TrustAnchors trustAnchors,
                                            ValidatedRpkiObjects validatedRpkiObjects,
                                            Storage storage) {
        this.rpkiObjects = rpkiObjects;
        this.rpkiRepositories = rpkiRepositories;
        this.settings = settings;
        this.validationScheduler = validationScheduler;
        this.validationRuns = validationRuns;
        this.trustAnchors = trustAnchors;
        this.validatedRpkiObjects = validatedRpkiObjects;
        this.storage = storage;
    }

    public void validate(long trustAnchorId) {
        Optional<TrustAnchor> maybeTrustAnchor = storage.readTx(tx -> trustAnchors.get(tx, Key.of(trustAnchorId)));
        if (!maybeTrustAnchor.isPresent()) {
            log.error("Couldn't find trust anchor {}", trustAnchorId);
            return;
        }
        validateTa(maybeTrustAnchor.get());
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

            URI locationUri = Objects.firstNonNull(trustAnchorCertificate.getRrdpNotifyUri(), trustAnchorCertificate.getRepositoryUri());
            validationResult.warnIfNull(locationUri, VALIDATOR_TRUST_ANCHOR_CERTIFICATE_RRDP_NOTIFY_URI_OR_REPOSITORY_URI_PRESENT);
            if (locationUri == null) {
                return;
            }

            final List<Key> rpkiObjectsKeys = validateCertificateAuthority(trustAnchor, registeredRepositories, context, validationResult);
            log.info("Benchmark: \n{}", Bench.dump());
            if (rpkiObjectsKeys.isEmpty()) {
                if (isValidationRunCompleted(validationResult)) {
                    log.info("No associated objects, validation run: {}, validation result: {}", validationRun.key(), validationResult);
                }
            }
            storage.writeTx0(tx -> {
                validationRuns.add(tx, validationRun);
                Long t = Time.timed(() -> rpkiObjectsKeys.forEach(key -> validationRuns.associateRpkiObjectKey(tx, validationRun, key)));
                log.info("Associated {} objects with the validation run {} in {}ms", rpkiObjectsKeys.size(), validationRun.key(), t);

                markTaObjectsReachable(tx, trustAnchorCertificate);

                Long tmr = Time.timed(() -> rpkiObjects.markReachable(tx, rpkiObjectsKeys));
                log.info("Marked {} objects as reachable in {}ms", rpkiObjectsKeys.size(), tmr);

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
            long end = System.currentTimeMillis();
            log.info("Tree validation {} for {} in {}ms", validationRun.getStatus().toString().toLowerCase(), trustAnchor.getName(), (end - begin));
        }
    }

    private void markTaObjectsReachable(Tx.Write tx, X509ResourceCertificate taCertificate) {
        final Instant now = Instant.now();
        rpkiObjects.findLatestMftByAKI(tx, taCertificate.getSubjectKeyIdentifier())
            .ifPresent(manifest -> {
                rpkiObjects.markReachable(tx, manifest.key(), now);
                rpkiObjects.findCertificateRepositoryObject(tx, manifest.key(), ManifestCms.class, ValidationResult.withLocation("ta-manifest.mft"))
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

    private List<Key> validateCertificateAuthority(TrustAnchor trustAnchor,
                                                   Map<URI, RpkiRepository> registeredRepositories,
                                                   CertificateRepositoryObjectValidationContext context,
                                                   ValidationResult validationResult) {
        final List<Key> validatedObjects = new ArrayList<>();

        ValidationLocation certificateLocation = validationResult.getCurrentLocation();
        ValidationResult temporary = ValidationResult.withLocation(certificateLocation);
        try {
            RpkiRepository rpkiRepository = Bench.mark("registerRepository in TX", () ->
                storage.writeTx(tx ->
                    Bench.mark("registerRepository", () ->
                        registerRepository(tx, trustAnchor, registeredRepositories, context))));

            temporary.warnIfTrue(rpkiRepository.isPending(), VALIDATOR_RPKI_REPOSITORY_PENDING, rpkiRepository.getLocationUri());
            if (rpkiRepository.isPending()) {
                return validatedObjects;
            }

            X509ResourceCertificate certificate = context.getCertificate();
            URI manifestUri = certificate.getManifestUri();
            temporary.setLocation(new ValidationLocation(manifestUri));

            Optional<RpkiObject> manifestObject = Bench.mark("findLatestMftByAKI in TX", () ->
                storage.readTx(tx -> rpkiObjects.findLatestMftByAKI(tx, certificate.getSubjectKeyIdentifier())));

            if (!manifestObject.isPresent()) {
                if (rpkiRepository.getStatus() == RpkiRepository.Status.FAILED) {
                    temporary.error(ValidationString.VALIDATOR_NO_MANIFEST_REPOSITORY_FAILED, rpkiRepository.getLocationUri());
                } else {
                    temporary.error(ValidationString.VALIDATOR_NO_LOCAL_MANIFEST_NO_MANIFEST_IN_REPOSITORY, rpkiRepository.getLocationUri());
                }
            }

            Optional<ManifestCms> maybeManifest = Bench.mark("findCertificateRepositoryObject in TX", () ->
                storage.readTx(tx -> manifestObject.flatMap(x ->
                    Bench.mark("findCertificateRepositoryObject", () ->
                        rpkiObjects.findCertificateRepositoryObject(tx, x.key(), ManifestCms.class, temporary)))));

            temporary.rejectIfTrue(manifestObject.isPresent() &&
                            rpkiRepository.getStatus() == RpkiRepository.Status.FAILED &&
                            maybeManifest.isPresent() &&
                            maybeManifest.get().isPastValidityTime(),
                    ValidationString.VALIDATOR_OLD_LOCAL_MANIFEST_REPOSITORY_FAILED, rpkiRepository.getLocationUri());

            if (temporary.hasFailureForCurrentLocation()) {
                return validatedObjects;
            }

            final ManifestCms manifest = maybeManifest.get();
            List<Map.Entry<String, byte[]>> crlEntries = manifest.getFiles().entrySet().stream()
                    .filter(entry -> RepositoryObjectType.parse(entry.getKey()) == RepositoryObjectType.Crl)
                    .collect(toList());
            temporary.rejectIfFalse(crlEntries.size() == 1, VALIDATOR_MANIFEST_CONTAINS_ONE_CRL_ENTRY, String.valueOf(crlEntries.size()));
            if (temporary.hasFailureForCurrentLocation()) {
                return validatedObjects;
            }

            Map.Entry<String, byte[]> crlEntry = crlEntries.get(0);
            URI crlUri = manifestUri.resolve(crlEntry.getKey());

            Optional<RpkiObject> crlObject = storage.readTx(tx -> rpkiObjects.findBySha256(tx, crlEntry.getValue()));
            temporary.rejectIfFalse(crlObject.isPresent(), VALIDATOR_CRL_FOUND, crlUri.toASCIIString());
            if (temporary.hasFailureForCurrentLocation()) {
                return validatedObjects;
            }

            temporary.setLocation(new ValidationLocation(crlUri));
            Optional<X509Crl> crl = Bench.mark("findCertificateRepositoryObject in TX", () ->
                crlObject.flatMap(x -> storage.readTx(tx ->
                    Bench.mark("findCertificateRepositoryObject", () ->
                        rpkiObjects.findCertificateRepositoryObject(tx, x.key(), X509Crl.class, temporary)))));

            if (temporary.hasFailureForCurrentLocation()) {
                return validatedObjects;
            }

            crl.get().validate(crlUri.toASCIIString(), context, null, VALIDATION_OPTIONS, temporary);
            if (temporary.hasFailureForCurrentLocation()) {
                return validatedObjects;
            }

            temporary.setLocation(new ValidationLocation(manifestUri));
            manifest.validate(manifestUri.toASCIIString(), context, crl.get(), manifest.getCrlUri(), VALIDATION_OPTIONS, temporary);
            if (temporary.hasFailureForCurrentLocation()) {
                return validatedObjects;
            }
            validatedObjects.add(manifestObject.get().key());

            List<CertificateRepositoryObjectValidationContext> objectStream = Bench.mark("retrieveManifestEntries in TX", () -> storage.readTx(tx ->
                Bench.mark("retrieveManifestEntries", () ->
                    retrieveManifestEntries(tx, manifest, manifestUri, temporary)
                        .entrySet().stream().map(e -> {
                        URI location = e.getKey();
                        RpkiObject rpkiObject = e.getValue();
                        temporary.setLocation(new ValidationLocation(location));

                        Optional<CertificateRepositoryObject> maybeCertificateRepositoryObject =
                            rpkiObjects.findCertificateRepositoryObject(tx, rpkiObject.key(), CertificateRepositoryObject.class, temporary);

                        if (!temporary.hasFailureForCurrentLocation()) {
                            return maybeCertificateRepositoryObject.flatMap(certificateRepositoryObject -> {
                                certificateRepositoryObject.validate(location.toASCIIString(), context, crl.get(), crlUri, VALIDATION_OPTIONS, temporary);

                                if (!temporary.hasFailureForCurrentLocation()) {
                                    validatedObjects.add(rpkiObject.key());
                                }

                                if (certificateRepositoryObject instanceof X509ResourceCertificate
                                    && ((X509ResourceCertificate) certificateRepositoryObject).isCa()
                                    && !temporary.hasFailureForCurrentLocation()) {

                                    return Optional.of(context.createChildContext(location, (X509ResourceCertificate) certificateRepositoryObject));
                                }
                                return Optional.empty();
                            });
                        }
                        return Optional.<CertificateRepositoryObjectValidationContext>empty();
                    })
                        .filter(Optional::isPresent)
                        .map(Optional::get)
                        .collect(Collectors.toList()))));

            objectStream
                .parallelStream()
                .map(childContext -> validateCertificateAuthority(trustAnchor, registeredRepositories, childContext, temporary))
                .forEachOrdered(validatedObjects::addAll);

        } catch (Exception e) {
            synchronized (this) {
                validationResult.error(ErrorCodes.UNHANDLED_EXCEPTION, e.toString(), ExceptionUtils.getStackTrace(e));
            }
        } finally {
            synchronized (this) {
                validationResult.addAll(temporary);
            }
        }

        return validatedObjects;
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
        log.info("Pre-loaded {} repositories in {}ms", registeredRepositories.size(), t);
        return registeredRepositories;
    }

    private Map<URI, RpkiObject> retrieveManifestEntries(Tx.Read tx, ManifestCms manifest, URI manifestUri, ValidationResult validationResult) {
        Map<URI, RpkiObject> result = new LinkedHashMap<>();
        for (Map.Entry<String, byte[]> entry : manifest.getFiles().entrySet()) {
            URI location = manifestUri.resolve(entry.getKey());
            validationResult.setLocation(new ValidationLocation(location));

            Optional<RpkiObject> object = rpkiObjects.findBySha256(tx, entry.getValue());
            validationResult.rejectIfFalse(object.isPresent(), VALIDATOR_MANIFEST_ENTRY_FOUND, manifestUri.toASCIIString());

            object.ifPresent(obj -> {
                boolean hashMatches = Arrays.equals(obj.getSha256(), entry.getValue());
                validationResult.rejectIfFalse(hashMatches, VALIDATOR_MANIFEST_ENTRY_HASH_MATCHES, entry.getKey());
                if (hashMatches) {
                    result.put(location, obj);
                }
            });
        }
        return result;
    }


    // Stuff for testing and benchmarking
    private List<Key> traverseCertificateAuthorityNoValidation(TrustAnchor trustAnchor,
                                                   Map<URI, RpkiRepository> registeredRepositories,
                                                   CertificateRepositoryObjectValidationContext context,
                                                   ValidationResult validationResult) {
        final List<Key> validatedObjects = new ArrayList<>();

        ValidationLocation certificateLocation = validationResult.getCurrentLocation();
        ValidationResult temporary = ValidationResult.withLocation(certificateLocation);
        try {
            RpkiRepository rpkiRepository = storage.writeTx(tx -> registerRepository(tx, trustAnchor, registeredRepositories, context));

            temporary.warnIfTrue(rpkiRepository.isPending(), VALIDATOR_RPKI_REPOSITORY_PENDING, rpkiRepository.getLocationUri());
            if (rpkiRepository.isPending()) {
                return validatedObjects;
            }

            X509ResourceCertificate certificate = context.getCertificate();
            URI manifestUri = certificate.getManifestUri();
            temporary.setLocation(new ValidationLocation(manifestUri));

            Optional<RpkiObject> manifestObject = storage.readTx(tx -> rpkiObjects.findLatestMftByAKI(tx, certificate.getSubjectKeyIdentifier()));

            if (!manifestObject.isPresent()) {
                if (rpkiRepository.getStatus() == RpkiRepository.Status.FAILED) {
                    temporary.error(ValidationString.VALIDATOR_NO_MANIFEST_REPOSITORY_FAILED, rpkiRepository.getLocationUri());
                } else {
                    temporary.error(ValidationString.VALIDATOR_NO_LOCAL_MANIFEST_NO_MANIFEST_IN_REPOSITORY, rpkiRepository.getLocationUri());
                }
            }

            Optional<ManifestCms> maybeManifest = storage.readTx(tx -> manifestObject.flatMap(x ->
                    rpkiObjects.findCertificateRepositoryObject(tx, x.key(), ManifestCms.class, temporary)));

            temporary.rejectIfTrue(manifestObject.isPresent() &&
                            rpkiRepository.getStatus() == RpkiRepository.Status.FAILED &&
                            maybeManifest.isPresent() &&
                            maybeManifest.get().isPastValidityTime(),
                    ValidationString.VALIDATOR_OLD_LOCAL_MANIFEST_REPOSITORY_FAILED, rpkiRepository.getLocationUri());

            if (temporary.hasFailureForCurrentLocation()) {
                return validatedObjects;
            }

            final ManifestCms manifest = maybeManifest.get();
            List<Map.Entry<String, byte[]>> crlEntries = manifest.getFiles().entrySet().stream()
                    .filter(entry -> RepositoryObjectType.parse(entry.getKey()) == RepositoryObjectType.Crl)
                    .collect(toList());
            temporary.rejectIfFalse(crlEntries.size() == 1, VALIDATOR_MANIFEST_CONTAINS_ONE_CRL_ENTRY, String.valueOf(crlEntries.size()));
            if (temporary.hasFailureForCurrentLocation()) {
                return validatedObjects;
            }

            Map.Entry<String, byte[]> crlEntry = crlEntries.get(0);
            URI crlUri = manifestUri.resolve(crlEntry.getKey());

            Optional<RpkiObject> crlObject = storage.readTx(tx -> rpkiObjects.findBySha256(tx, crlEntry.getValue()));
            temporary.rejectIfFalse(crlObject.isPresent(), VALIDATOR_CRL_FOUND, crlUri.toASCIIString());
            if (temporary.hasFailureForCurrentLocation()) {
                return validatedObjects;
            }

            temporary.setLocation(new ValidationLocation(crlUri));
            Optional<X509Crl> crl = crlObject.flatMap(x -> storage.readTx(tx -> rpkiObjects.findCertificateRepositoryObject(tx, x.key(), X509Crl.class, temporary)));
            if (temporary.hasFailureForCurrentLocation()) {
                return validatedObjects;
            }

//            crl.get().validate(crlUri.toASCIIString(), context, null, VALIDATION_OPTIONS, temporary);
//            if (temporary.hasFailureForCurrentLocation()) {
//                return validatedObjects;
//            }

//            temporary.setLocation(new ValidationLocation(manifestUri));
//            manifest.validate(manifestUri.toASCIIString(), context, crl.get(), manifest.getCrlUri(), VALIDATION_OPTIONS, temporary);
//            if (temporary.hasFailureForCurrentLocation()) {
//                return validatedObjects;
//            }
            validatedObjects.add(manifestObject.get().key());

            List<CertificateRepositoryObjectValidationContext> objectStream = storage.readTx(tx ->
                    retrieveManifestEntries(tx, manifest, manifestUri, temporary).entrySet().stream().map(e -> {
                        URI location = e.getKey();
                        RpkiObject rpkiObject = e.getValue();
                        temporary.setLocation(new ValidationLocation(location));

                        Optional<CertificateRepositoryObject> maybeCertificateRepositoryObject =
                                rpkiObjects.findCertificateRepositoryObject(tx, rpkiObject.key(), CertificateRepositoryObject.class, temporary);

                        if (!temporary.hasFailureForCurrentLocation()) {
                            return maybeCertificateRepositoryObject.flatMap(certificateRepositoryObject -> {
//                                certificateRepositoryObject.validate(location.toASCIIString(), context, crl.get(), crlUri, VALIDATION_OPTIONS, temporary);

                                if (!temporary.hasFailureForCurrentLocation()) {
                                    validatedObjects.add(rpkiObject.key());
                                }

                                if (certificateRepositoryObject instanceof X509ResourceCertificate
                                        && ((X509ResourceCertificate) certificateRepositoryObject).isCa()
                                        && !temporary.hasFailureForCurrentLocation()) {

                                    return Optional.of(context.createChildContext(location, (X509ResourceCertificate) certificateRepositoryObject));
                                }
                                return Optional.empty();
                            });
                        }
                        return Optional.<CertificateRepositoryObjectValidationContext>empty();
                    })
                            .filter(Optional::isPresent)
                            .map(Optional::get)
                            .collect(Collectors.toList()));

            objectStream
                    .stream()
                    .map(childContext -> traverseCertificateAuthorityNoValidation(trustAnchor, registeredRepositories, childContext, temporary))
                    .forEachOrdered(validatedObjects::addAll);

        } catch (Exception e) {
            synchronized (this) {
                validationResult.error(ErrorCodes.UNHANDLED_EXCEPTION, e.toString(), ExceptionUtils.getStackTrace(e));
            }
        } finally {
            synchronized (this) {
                validationResult.addAll(temporary);
            }
        }

        return validatedObjects;
    }



}
