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
import net.ripe.rpki.validator3.storage.data.Key;
import net.ripe.rpki.validator3.storage.Lmdb;
import net.ripe.rpki.validator3.storage.data.Ref;
import net.ripe.rpki.validator3.storage.data.RpkiObject;
import net.ripe.rpki.validator3.storage.data.RpkiRepository;
import net.ripe.rpki.validator3.storage.data.TrustAnchor;
import net.ripe.rpki.validator3.storage.data.validation.CertificateTreeValidationRun;
import net.ripe.rpki.validator3.storage.lmdb.Tx;
import net.ripe.rpki.validator3.storage.stores.RpkiObjectStore;
import net.ripe.rpki.validator3.storage.stores.RpkiRepositoryStore;
import net.ripe.rpki.validator3.storage.stores.SettingsStore;
import net.ripe.rpki.validator3.storage.stores.TrustAnchorStore;
import net.ripe.rpki.validator3.storage.stores.ValidationRunStore;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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

/**
 * TODO Decouple readings from writing in validation
 *  descend to allow parallel validations.
 */
@Service
@Slf4j
public class CertificateTreeValidationService {
    private static final ValidationOptions VALIDATION_OPTIONS = new ValidationOptions();

    private final RpkiObjectStore rpkiObjectStore;
    private final RpkiRepositoryStore rpkiRepositoryStore;
    private final SettingsStore settingsStore;
    private final ValidationScheduler validationScheduler;
    private final ValidationRunStore validationRunStore;
    private final TrustAnchorStore trustAnchorStore;
    private final Lmdb lmdb;
    private final ValidatedRpkiObjects validatedRpkiObjects;

    @Autowired
    public CertificateTreeValidationService(RpkiObjectStore rpkiObjectStore,
                                            RpkiRepositoryStore rpkiRepositoryStore,
                                            SettingsStore settingsStore,
                                            ValidationScheduler validationScheduler,
                                            ValidationRunStore validationRunStore,
                                            TrustAnchorStore trustAnchorStore,
                                            ValidatedRpkiObjects validatedRpkiObjects,
                                            Lmdb lmdb) {
        this.rpkiObjectStore = rpkiObjectStore;
        this.rpkiRepositoryStore = rpkiRepositoryStore;
        this.settingsStore = settingsStore;
        this.validationScheduler = validationScheduler;
        this.validationRunStore = validationRunStore;
        this.trustAnchorStore = trustAnchorStore;
        this.validatedRpkiObjects = validatedRpkiObjects;
        this.lmdb = lmdb;
    }

    public void validate(long trustAnchorId) {
            Optional<TrustAnchor> maybeTrustAnchor = lmdb.readTx(tx -> trustAnchorStore.get(tx, Key.of(trustAnchorId)));
            if (!maybeTrustAnchor.isPresent()) {
                log.error("Couldn't find trust anchor {}", trustAnchorId);
                return;
            }
            validateTa(maybeTrustAnchor.get());
    }

    private void validateTa(TrustAnchor trustAnchor) {
        log.info("Starting tree validation for {}", trustAnchor);

        final Map<URI, RpkiRepository> registeredRepositories = new ConcurrentHashMap<>();

        final Ref<TrustAnchor> trustAnchorRef = lmdb.readTx(tx -> trustAnchorStore.makeRef(tx, trustAnchor.getId()));
        final CertificateTreeValidationRun validationRun = lmdb.writeTx(tx ->
                validationRunStore.add(tx, new CertificateTreeValidationRun(trustAnchorRef)));

        String trustAnchorLocation = trustAnchor.getLocations().get(0);
        ValidationResult validationResult = ValidationResult.withLocation(trustAnchorLocation);

        try {
            X509ResourceCertificate certificate = trustAnchor.getCertificate();
            validationResult.rejectIfNull(certificate, VALIDATOR_TRUST_ANCHOR_CERTIFICATE_AVAILABLE);
            if (certificate == null) {
                return;
            }

            CertificateRepositoryObjectValidationContext context = new CertificateRepositoryObjectValidationContext(
                    URI.create(trustAnchorLocation),
                    certificate
            );

            certificate.validate(trustAnchorLocation, context, null, null, VALIDATION_OPTIONS, validationResult);
            if (validationResult.hasFailureForCurrentLocation()) {
                return;
            }

            URI locationUri = Objects.firstNonNull(certificate.getRrdpNotifyUri(), certificate.getRepositoryUri());
            validationResult.warnIfNull(locationUri, VALIDATOR_TRUST_ANCHOR_CERTIFICATE_RRDP_NOTIFY_URI_OR_REPOSITORY_URI_PRESENT);
            if (locationUri == null) {
                return;
            }

            final List<RpkiObject> rpkiObjects = validateCertificateAuthority(trustAnchor, registeredRepositories, context, validationResult);
            lmdb.writeTx0(tx -> {
                rpkiObjects.forEach(o -> validationRunStore.associate(tx, validationRun, o));

                if (isValidationRunCompleted(validationResult)) {
                    trustAnchor.markInitialCertificateTreeValidationRunCompleted();
                    trustAnchorStore.update(tx, trustAnchor);
                    if (!settingsStore.isInitialValidationRunCompleted(tx) && trustAnchorStore.allInitialCertificateTreeValidationRunsCompleted(tx)) {
                        settingsStore.markInitialValidationRunCompleted(tx);
                        log.info("All trust anchors have completed their initial certificate tree validation run, validator is now ready");
                    }
                }

                validatedRpkiObjects.update(tx, trustAnchorRef, rpkiObjects);
            });
        } finally {
            validationRun.completeWith(validationResult);
            lmdb.writeTx0(tx -> validationRunStore.update(tx, validationRun));
            log.info("Tree validation {} for {}", validationRun.getStatus().toString().toLowerCase(), trustAnchor);
        }
    }

    private boolean isValidationRunCompleted(ValidationResult validationResult) {
        return validationResult.getWarnings().stream()
                .noneMatch(check -> check.getStatus() != ValidationStatus.PASSED && VALIDATOR_RPKI_REPOSITORY_PENDING.equals(check.getKey()));
    }

    private List<RpkiObject> validateCertificateAuthority(TrustAnchor trustAnchor,
                                                          Map<URI, RpkiRepository> registeredRepositories,
                                                          CertificateRepositoryObjectValidationContext context,
                                                          ValidationResult validationResult) {
        final List<RpkiObject> validatedObjects = new ArrayList<>();

        ValidationLocation certificateLocation = validationResult.getCurrentLocation();
        ValidationResult temporary = ValidationResult.withLocation(certificateLocation);
        try {
            RpkiRepository rpkiRepository = registerRepository(trustAnchor, registeredRepositories, context);

            temporary.warnIfTrue(rpkiRepository.isPending(), VALIDATOR_RPKI_REPOSITORY_PENDING, rpkiRepository.getLocationUri());
            if (rpkiRepository.isPending()) {
                return validatedObjects;
            }

            X509ResourceCertificate certificate = context.getCertificate();
            URI manifestUri = certificate.getManifestUri();
            temporary.setLocation(new ValidationLocation(manifestUri));


            Optional<RpkiObject> manifestObject = lmdb.readTx(tx -> rpkiObjectStore.findLatestByTypeAndAuthorityKeyIdentifier(tx,
                    RpkiObject.Type.MFT, context.getSubjectKeyIdentifier()));

            if (!manifestObject.isPresent()) {
                if (rpkiRepository.getStatus() == RpkiRepository.Status.FAILED) {
                    temporary.error(ValidationString.VALIDATOR_NO_MANIFEST_REPOSITORY_FAILED, rpkiRepository.getLocationUri());
                } else {
                    temporary.error(ValidationString.VALIDATOR_NO_LOCAL_MANIFEST_NO_MANIFEST_IN_REPOSITORY, rpkiRepository.getLocationUri());
                }
            }

            Optional<ManifestCms> maybeManifest = lmdb.readTx(tx -> manifestObject.flatMap(x ->
                    rpkiObjectStore.findCertificateRepositoryObject(tx, x.key(), ManifestCms.class, temporary)));

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

            Optional<RpkiObject> crlObject = lmdb.readTx(tx -> rpkiObjectStore.findBySha256(tx, crlEntry.getValue()));
            temporary.rejectIfFalse(crlObject.isPresent(), VALIDATOR_CRL_FOUND, crlUri.toASCIIString());
            if (temporary.hasFailureForCurrentLocation()) {
                return validatedObjects;
            }

            temporary.setLocation(new ValidationLocation(crlUri));
            Optional<X509Crl> crl = crlObject.flatMap(x -> lmdb.readTx(tx -> rpkiObjectStore.findCertificateRepositoryObject(tx, x.key(), X509Crl.class, temporary)));
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
            validatedObjects.add(manifestObject.get());

            List<CertificateRepositoryObjectValidationContext> objectStream = lmdb.readTx(tx ->
                    retrieveManifestEntries(tx, manifest, manifestUri, temporary).entrySet().stream()
                            .map(e -> createChildValidationContext(tx, context, validatedObjects, temporary, crlUri, crl, e))
                            .filter(Optional::isPresent)
                            .map(Optional::get)
                            .collect(Collectors.toList()));

            objectStream.parallelStream().forEach(childContext ->
                    validatedObjects.addAll(validateCertificateAuthority(trustAnchor, registeredRepositories, childContext, temporary)));

        } catch (Exception e) {
            log.error("Something bad happened", e);
            validationResult.error(ErrorCodes.UNHANDLED_EXCEPTION, e.toString(), ExceptionUtils.getStackTrace(e));
        } finally {
            validationResult.addAll(temporary);
        }

        return validatedObjects;
    }

    private Optional<CertificateRepositoryObjectValidationContext> createChildValidationContext(Tx.Read tx,
                                                                                                CertificateRepositoryObjectValidationContext parentContext,
                                                                                                List<RpkiObject> validatedObjects,
                                                                                                ValidationResult validationResult,
                                                                                                URI crlUri,
                                                                                                Optional<X509Crl> crl,
                                                                                                Map.Entry<URI, RpkiObject> e) {
        URI location = e.getKey();
        RpkiObject obj = e.getValue();
        validationResult.setLocation(new ValidationLocation(location));

        Optional<CertificateRepositoryObject> maybeCertificateRepositoryObject =
                rpkiObjectStore.findCertificateRepositoryObject(tx, obj.key(), CertificateRepositoryObject.class, validationResult);

        if (!validationResult.hasFailureForCurrentLocation()) {
            return maybeCertificateRepositoryObject.flatMap(certificateRepositoryObject -> {
                certificateRepositoryObject.validate(location.toASCIIString(), parentContext, crl.get(), crlUri, VALIDATION_OPTIONS, validationResult);

                if (!validationResult.hasFailureForCurrentLocation()) {
                    validatedObjects.add(obj);
                }

                if (certificateRepositoryObject instanceof X509ResourceCertificate
                        && ((X509ResourceCertificate) certificateRepositoryObject).isCa()
                        && !validationResult.hasFailureForCurrentLocation()) {

                    return Optional.of(parentContext.createChildContext(location, (X509ResourceCertificate) certificateRepositoryObject));
                }
                return Optional.empty();
            });
        }
        return Optional.empty();
    }

    // Use async as a trick to perform writing Tx while being inside of a reading Tx
    private ExecutorService async = Executors.newSingleThreadExecutor();

    private RpkiRepository registerRepository(TrustAnchor trustAnchor,
                                              Map<URI, RpkiRepository> registeredRepositories,
                                              CertificateRepositoryObjectValidationContext context) throws Exception {
        return async.submit(() ->
                lmdb.writeTx(tx -> {
                    final Ref<TrustAnchor> trustAnchorRef = trustAnchorStore.makeRef(tx, trustAnchor.key());
                    if (context.getRpkiNotifyURI() != null) {
                        final RpkiRepository rpkiRepository = registeredRepositories.computeIfAbsent(context.getRpkiNotifyURI(),
                                uri -> rpkiRepositoryStore.register(tx, trustAnchorRef, uri.toASCIIString(), RRDP));

                        tx.onCommit(() -> validationScheduler.addRpkiRepository(rpkiRepository));
                        return rpkiRepository;
                    }
                    return registeredRepositories.computeIfAbsent(context.getRepositoryURI(),
                            uri -> rpkiRepositoryStore.register(tx, trustAnchorRef, uri.toASCIIString(), RSYNC));

                })).get();
    }

    private Map<URI, RpkiObject> retrieveManifestEntries(Tx.Read tx, ManifestCms manifest, URI manifestUri, ValidationResult validationResult) {
        Map<URI, RpkiObject> result = new LinkedHashMap<>();
        for (Map.Entry<String, byte[]> entry : manifest.getFiles().entrySet()) {
            URI location = manifestUri.resolve(entry.getKey());
            validationResult.setLocation(new ValidationLocation(location));

            Optional<RpkiObject> object = rpkiObjectStore.findBySha256(tx, entry.getValue());
            validationResult.rejectIfFalse(object.isPresent(), VALIDATOR_MANIFEST_ENTRY_FOUND, manifestUri.toASCIIString());

            object.ifPresent(obj -> {
                boolean hashMatches = Arrays.equals(obj.getSha256(), entry.getValue());
                validationResult.rejectIfFalse(hashMatches, VALIDATOR_MANIFEST_ENTRY_HASH_MATCHES, entry.getKey());
                if (!hashMatches) {
                    return;
                }
                result.put(location, obj);
            });
        }
        return result;
    }

}
