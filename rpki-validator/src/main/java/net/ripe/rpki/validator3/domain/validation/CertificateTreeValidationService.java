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
import net.ripe.rpki.validator3.domain.CertificateTreeValidationRun;
import net.ripe.rpki.validator3.domain.ErrorCodes;
import net.ripe.rpki.validator3.domain.RpkiObject;
import net.ripe.rpki.validator3.domain.RpkiObjects;
import net.ripe.rpki.validator3.domain.RpkiRepositories;
import net.ripe.rpki.validator3.domain.RpkiRepository;
import net.ripe.rpki.validator3.domain.Settings;
import net.ripe.rpki.validator3.domain.TrustAnchor;
import net.ripe.rpki.validator3.domain.TrustAnchors;
import net.ripe.rpki.validator3.domain.ValidatedRpkiObjects;
import net.ripe.rpki.validator3.domain.ValidationRuns;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.persistence.EntityManager;
import javax.persistence.FlushModeType;
import javax.transaction.Transactional;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.util.stream.Collectors.toList;
import static net.ripe.rpki.commons.validation.ValidationString.*;

@Service
@Slf4j
public class CertificateTreeValidationService {
    private static final ValidationOptions VALIDATION_OPTIONS = new ValidationOptions();

    @Autowired
    private EntityManager entityManager;
    @Autowired
    private RpkiObjects rpkiObjects;
    @Autowired
    private TrustAnchors trustAnchors;
    @Autowired
    private RpkiRepositories rpkiRepositories;
    @Autowired
    private ValidationRuns validationRuns;
    @Autowired
    private Settings settings;
    @Autowired
    private ValidatedRpkiObjects validatedRpkiObjects;

    @Transactional(Transactional.TxType.REQUIRED)
    public void validate(long trustAnchorId) {
        Map<URI, RpkiRepository> registeredRepositories = new HashMap<>();
        entityManager.setFlushMode(FlushModeType.COMMIT);

        TrustAnchor trustAnchor = trustAnchors.get(trustAnchorId);
        log.info("starting tree validation for {}", trustAnchor);

        CertificateTreeValidationRun validationRun = new CertificateTreeValidationRun(trustAnchor);
        validationRuns.add(validationRun);

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

            validationRun.getValidatedObjects().addAll(
                validateCertificateAuthority(trustAnchor, registeredRepositories, context, validationResult)
            );

            entityManager.setFlushMode(FlushModeType.AUTO);

            if (isValidationRunCompleted(validationResult)) {
                trustAnchor.markInitialCertificateTreeValidationRunCompleted();
                if (!settings.isInitialValidationRunCompleted() && trustAnchors.allInitialCertificateTreeValidationRunsCompleted()) {
                    settings.markInitialValidationRunCompleted();
                    log.info("All trust anchors have completed their initial certificate tree validation run, validator is now ready");
                }
            }

            validatedRpkiObjects.update(trustAnchor, validationRun.getValidatedObjects());
        } finally {
            validationRun.completeWith(validationResult);
            log.info("tree validation {} for {}", validationRun.getStatus(), trustAnchor);
        }
    }

    private boolean isValidationRunCompleted(ValidationResult validationResult) {
        for (net.ripe.rpki.commons.validation.ValidationCheck check: validationResult.getWarnings()) {
            if (check.getStatus() != ValidationStatus.PASSED && VALIDATOR_RPKI_REPOSITORY_PENDING.equals(check.getKey())) {
                return false;
            }
        }
        return true;
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

            Optional<RpkiObject> manifestObject = rpkiObjects.findLatestByTypeAndAuthorityKeyIdentifier(RpkiObject.Type.MFT, context.getSubjectKeyIdentifier());
            if (!manifestObject.isPresent()) {
                if (rpkiRepository.getStatus() == RpkiRepository.Status.FAILED) {
                    temporary.error(ValidationString.VALIDATOR_NO_MANIFEST_REPOSITORY_FAILED, rpkiRepository.getLocationUri());
                } else {
                    temporary.error(ValidationString.VALIDATOR_NO_LOCAL_MANIFEST_NO_MANIFEST_IN_REPOSITORY, rpkiRepository.getLocationUri());
                }
            }

            Optional<ManifestCms> maybeManifest = manifestObject.flatMap(x -> rpkiObjects.findCertificateRepositoryObject(x.getId(), ManifestCms.class, temporary));

            temporary.rejectIfTrue(manifestObject.isPresent() &&
                            rpkiRepository.getStatus() == RpkiRepository.Status.FAILED &&
                            maybeManifest.isPresent() &&
                            maybeManifest.get().isPastValidityTime(),
                    ValidationString.VALIDATOR_OLD_LOCAL_MANIFEST_REPOSITORY_FAILED, rpkiRepository.getLocationUri());

            if (temporary.hasFailureForCurrentLocation()) {
                return validatedObjects;
            }

            ManifestCms manifest = maybeManifest.get();
            List<Map.Entry<String, byte[]>> crlEntries = manifest.getFiles().entrySet().stream()
                .filter((entry) -> RepositoryObjectType.parse(entry.getKey()) == RepositoryObjectType.Crl)
                .collect(toList());
            temporary.rejectIfFalse(crlEntries.size() == 1, VALIDATOR_MANIFEST_CONTAINS_ONE_CRL_ENTRY, String.valueOf(crlEntries.size()));
            if (temporary.hasFailureForCurrentLocation()) {
                return validatedObjects;
            }

            Map.Entry<String, byte[]> crlEntry = crlEntries.get(0);
            URI crlUri = manifestUri.resolve(crlEntry.getKey());

            Optional<RpkiObject> crlObject = rpkiObjects.findBySha256(crlEntry.getValue());
            temporary.rejectIfFalse(crlObject.isPresent(), VALIDATOR_CRL_FOUND, crlUri.toASCIIString());
            if (temporary.hasFailureForCurrentLocation()) {
                return validatedObjects;
            }

            temporary.setLocation(new ValidationLocation(crlUri));
            Optional<X509Crl> crl = crlObject.flatMap(x -> rpkiObjects.findCertificateRepositoryObject(x.getId(), X509Crl.class, temporary));
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

            Map<URI, RpkiObject> manifestEntries = retrieveManifestEntries(manifest, manifestUri, temporary);

            manifestEntries.forEach((location, obj) -> {
                temporary.setLocation(new ValidationLocation(location));

                Optional<CertificateRepositoryObject> maybeCertificateRepositoryObject = rpkiObjects.findCertificateRepositoryObject(obj.getId(), CertificateRepositoryObject.class, temporary);
                if (temporary.hasFailureForCurrentLocation()) {
                    return;
                }

                maybeCertificateRepositoryObject.ifPresent(certificateRepositoryObject -> {
                    certificateRepositoryObject.validate(location.toASCIIString(), context, crl.get(), crlUri, VALIDATION_OPTIONS, temporary);

                    if (!temporary.hasFailureForCurrentLocation()) {
                        validatedObjects.add(obj);
                    }

                    if (certificateRepositoryObject instanceof X509ResourceCertificate
                        && ((X509ResourceCertificate) certificateRepositoryObject).isCa()
                        && !temporary.hasFailureForCurrentLocation()) {

                        CertificateRepositoryObjectValidationContext childContext = context.createChildContext(location, (X509ResourceCertificate) certificateRepositoryObject);
                        validatedObjects.addAll(validateCertificateAuthority(trustAnchor, registeredRepositories, childContext, temporary));
                    }
                });
            });
        } catch (Exception e) {
            log.debug("e", e);
            validationResult.error(ErrorCodes.UNHANDLED_EXCEPTION, e.toString(), ExceptionUtils.getStackTrace(e));
        } finally {
            validationResult.addAll(temporary);
        }

        return validatedObjects;
    }

    private RpkiRepository registerRepository(TrustAnchor trustAnchor, Map<URI, RpkiRepository> registeredRepositories, CertificateRepositoryObjectValidationContext context) {
        if (context.getRpkiNotifyURI() != null) {
            return registeredRepositories.computeIfAbsent(context.getRpkiNotifyURI(), (uri) -> rpkiRepositories.register(
                trustAnchor,
                uri.toASCIIString(),
                RpkiRepository.Type.RRDP
            ));
        } else {
            return registeredRepositories.computeIfAbsent(context.getRepositoryURI(), (uri) -> rpkiRepositories.register(
                trustAnchor,
                uri.toASCIIString(),
                RpkiRepository.Type.RSYNC
            ));
        }
    }

    private Map<URI, RpkiObject> retrieveManifestEntries(ManifestCms manifest, URI manifestUri, ValidationResult validationResult) {
        Map<URI, RpkiObject> result = new LinkedHashMap<>();
        for (Map.Entry<String, byte[]> entry : manifest.getFiles().entrySet()) {
            URI location = manifestUri.resolve(entry.getKey());
            validationResult.setLocation(new ValidationLocation(location));

            Optional<RpkiObject> object = rpkiObjects.findBySha256(entry.getValue());
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
