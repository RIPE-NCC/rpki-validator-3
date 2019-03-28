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

import com.google.common.collect.Sets;
import com.google.common.io.Files;
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
import net.ripe.rpki.validator3.storage.Lmdb;
import net.ripe.rpki.validator3.storage.data.Ref;
import net.ripe.rpki.validator3.storage.data.RpkiObject;
import net.ripe.rpki.validator3.storage.data.RpkiRepository;
import net.ripe.rpki.validator3.storage.data.SId;
import net.ripe.rpki.validator3.storage.data.TrustAnchor;
import net.ripe.rpki.validator3.storage.data.validation.TrustAnchorValidationRun;
import net.ripe.rpki.validator3.storage.data.validation.ValidationCheck;
import net.ripe.rpki.validator3.storage.lmdb.Tx;
import net.ripe.rpki.validator3.storage.stores.Id;
import net.ripe.rpki.validator3.storage.stores.RpkiRepositoryStore;
import net.ripe.rpki.validator3.storage.stores.TrustAnchorStore;
import net.ripe.rpki.validator3.storage.stores.ValidationRunStore;
import net.ripe.rpki.validator3.util.Rsync;
import net.ripe.rpki.validator3.util.Transactions;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.security.GeneralSecurityException;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

@Service
@Slf4j
public class TrustAnchorValidationService {

    private final TrustAnchorStore trustAnchorStore;
    private final RpkiRepositoryStore rpkiRepositoryStore;
    private final ValidationRunStore validationRunStore;
    private final ValidationScheduler validationScheduler;

//    private final TrustAnchors trustAnchorRepository;
//    private final ValidationRuns validationRunRepository;
    private final File localRsyncStorageDirectory;
    private final RpkiRepositoryValidationService repositoryValidationService;
    private final Lmdb lmdb;

    @Autowired
    public TrustAnchorValidationService(
            TrustAnchorStore trustAnchorStore,
            RpkiRepositoryStore rpkiRepositoryStore,
            ValidationRunStore validationRunStore,
            ValidationScheduler validationScheduler,
            @Value("${rpki.validator.rsync.local.storage.directory}") File localRsyncStorageDirectory,
            RpkiRepositoryValidationService repositoryValidationService,
            Lmdb lmdb) {
        this.trustAnchorStore = trustAnchorStore;
        this.rpkiRepositoryStore = rpkiRepositoryStore;
        this.validationRunStore = validationRunStore;
        this.validationScheduler = validationScheduler;
        this.localRsyncStorageDirectory = localRsyncStorageDirectory;
        this.repositoryValidationService = repositoryValidationService;
        this.lmdb = lmdb;
    }

    public void validate(long trustAnchorId) {
        Optional<TrustAnchor> maybeTrustAnchor = Tx.rwith(lmdb.readTx(), tx -> trustAnchorStore.get(tx, Id.key(trustAnchorId)));
        if (!maybeTrustAnchor.isPresent()) {
            log.error("Trust anchor {} doesn't exist.", trustAnchorId);
            return;
        }

        TrustAnchor trustAnchor = maybeTrustAnchor.get();
        log.info("trust anchor {} located at {} with subject public key info {}", trustAnchor.getName(), trustAnchor.getLocations(), trustAnchor.getSubjectPublicKeyInfo());

        TrustAnchorValidationRun validationRun = Tx.rwith(lmdb.readTx(), tx -> {
            final Ref trustAnchorRef = trustAnchorStore.makeRef(tx, SId.of(trustAnchorId));
            return new TrustAnchorValidationRun(trustAnchorRef, trustAnchor.getLocations().get(0));
        });
        Tx.use(lmdb.writeTx(), tx -> validationRunStore.add(tx, validationRun));

        try {
            boolean updated = false;

            URI trustAnchorCertificateURI = URI.create(validationRun.getTrustAnchorCertificateURI()).normalize();
            ValidationResult validationResult = ValidationResult.withLocation(trustAnchorCertificateURI);

            File targetFile = fetchTrustAnchorCertificate(trustAnchorCertificateURI, validationResult);
            if (!validationResult.hasFailureForCurrentLocation()) {
                long trustAnchorCertificateSize = targetFile.length();

                if (trustAnchorCertificateSize < RpkiObject.MIN_SIZE) {
                    validationResult.error(ErrorCodes.REPOSITORY_OBJECT_MINIMUM_SIZE, trustAnchorCertificateURI.toASCIIString(), String.valueOf(trustAnchorCertificateSize), String.valueOf(RpkiObject.MIN_SIZE));
                } else if (trustAnchorCertificateSize > RpkiObject.MAX_SIZE) {
                    validationResult.error(ErrorCodes.REPOSITORY_OBJECT_MAXIMUM_SIZE, trustAnchorCertificateURI.toASCIIString(), String.valueOf(trustAnchorCertificateSize), String.valueOf(RpkiObject.MAX_SIZE));
                } else {
                    final X509ResourceCertificate certificate = parseCertificate(trustAnchor, targetFile, validationResult);

                    if (!validationResult.hasFailureForCurrentLocation()) {
                        // validity time?
                        int comparedSerial = trustAnchor.getCertificate() == null ? 1 : trustAnchor.getCertificate().getSerialNumber().compareTo(certificate.getSerialNumber());
                        validationResult.warnIfTrue(comparedSerial < 0, ValidationString.VALIDATOR_REPOSITORY_OBJECT_IS_OLDER_THAN_PREVIOUS_OBJECT, trustAnchorCertificateURI.toASCIIString());
                        if (comparedSerial > 0) {
                            log.info("Setting certificate {} for the TA {}", trustAnchorCertificateURI, trustAnchor.getName());
                            trustAnchor.setCertificate(certificate);
                            updated = true;
                        }
                    }
                }
            }

            if (validationResult.hasFailures()) {
                log.warn("Validation result for the TA {} has failures: {}", trustAnchor.getName(),
                        validationResult.getFailures(new ValidationLocation(trustAnchorCertificateURI)));
            }

            validationRun.completeWith(validationResult);
            if (updated) {
                final Set<TrustAnchor> affectedTrustAnchors = Sets.newHashSet(trustAnchor);
                if (trustAnchor.getRsyncPrefetchUri() != null) {
                    Optional<RpkiRepository> byURI = Tx.rwith(lmdb.readTx(), tx ->
                            rpkiRepositoryStore.findByURI(tx, trustAnchor.getRsyncPrefetchUri()));
                    byURI.ifPresent(r -> affectedTrustAnchors.addAll(repositoryValidationService.prefetchRepository(r)));
                }
                Transactions.afterCommit(() -> affectedTrustAnchors.forEach(validationScheduler::triggerCertificateTreeValidation));
            }
        } catch (CommandExecutionException | IOException e) {
            log.error("validation run for trust anchor {} failed", trustAnchor, e);
            validationRun.addCheck(new ValidationCheck(validationRun.getTrustAnchorCertificateURI(), ValidationCheck.Status.ERROR, ErrorCodes.UNHANDLED_EXCEPTION, e.toString()));
            validationRun.setFailed();
        }

    }

    private X509ResourceCertificate parseCertificate(TrustAnchor trustAnchor, File certificateFile, ValidationResult validationResult) throws IOException {
        CertificateRepositoryObject trustAnchorCertificate = CertificateRepositoryObjectFactory.createCertificateRepositoryObject(Files.toByteArray(certificateFile), validationResult);
        validationResult.rejectIfFalse(trustAnchorCertificate instanceof X509ResourceCertificate, ErrorCodes.REPOSITORY_OBJECT_IS_TRUST_ANCHOR_CERTIFICATE, trustAnchor.getRsyncPrefetchUri());
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

        validationResult.rejectIfFalse(signatureValid, ErrorCodes.TRUST_ANCHOR_SIGNATURE, trustAnchor.getRsyncPrefetchUri(), trustAnchor.getSubjectPublicKeyInfo());

        return certificate;
    }

    private File fetchTrustAnchorCertificate(URI trustAnchorCertificateURI, ValidationResult validationResult) throws IOException {
        File targetFile = Rsync.localFileFromRsyncUri(localRsyncStorageDirectory, trustAnchorCertificateURI);
        if (targetFile.getParentFile().mkdirs()) {
            log.info("created local rsync storage directory {} for trust anchor {}", targetFile.getParentFile(), trustAnchorCertificateURI);
        }

        net.ripe.rpki.commons.rsync.Rsync rsync = new net.ripe.rpki.commons.rsync.Rsync(trustAnchorCertificateURI.toASCIIString(), targetFile.getPath());
        rsync.addOptions("--update", "--times", "--copy-links");
        int exitStatus = rsync.execute();
        if (exitStatus != 0) {
            validationResult.error(ErrorCodes.RSYNC_FETCH, String.valueOf(exitStatus), ArrayUtils.toString(rsync.getErrorLines()));
            return null;
        } else {
            log.info("Downloaded certificate {} to {}", trustAnchorCertificateURI, targetFile);
            return targetFile;
        }
    }
}
