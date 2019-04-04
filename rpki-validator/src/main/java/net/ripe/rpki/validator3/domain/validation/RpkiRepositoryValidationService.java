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
import net.ripe.rpki.commons.crypto.util.CertificateRepositoryObjectFactory;
import net.ripe.rpki.commons.validation.ValidationLocation;
import net.ripe.rpki.commons.validation.ValidationResult;
import net.ripe.rpki.validator3.background.ValidationScheduler;
import net.ripe.rpki.validator3.domain.ErrorCodes;
import net.ripe.rpki.validator3.rrdp.RrdpService;
import net.ripe.rpki.validator3.storage.data.Key;
import net.ripe.rpki.validator3.storage.Lmdb;
import net.ripe.rpki.validator3.storage.data.Ref;
import net.ripe.rpki.validator3.storage.data.RpkiObject;
import net.ripe.rpki.validator3.storage.data.RpkiRepository;
import net.ripe.rpki.validator3.storage.data.TrustAnchor;
import net.ripe.rpki.validator3.storage.data.validation.RpkiRepositoryValidationRun;
import net.ripe.rpki.validator3.storage.data.validation.RrdpRepositoryValidationRun;
import net.ripe.rpki.validator3.storage.data.validation.RsyncRepositoryValidationRun;
import net.ripe.rpki.validator3.storage.lmdb.Tx;
import net.ripe.rpki.validator3.storage.stores.RpkiObjectStore;
import net.ripe.rpki.validator3.storage.stores.RpkiRepositoryStore;
import net.ripe.rpki.validator3.storage.stores.TrustAnchorStore;
import net.ripe.rpki.validator3.storage.stores.ValidationRunStore;
import net.ripe.rpki.validator3.util.Hex;
import net.ripe.rpki.validator3.util.Rsync;
import net.ripe.rpki.validator3.util.Sha256;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.tuple.Triple;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@Slf4j
public class RpkiRepositoryValidationService {

    private final RrdpService rrdpService;
    private final File rsyncLocalStorageDirectory;
    private final Duration rsyncRepositoryDownloadInterval;
    private final ValidationRunStore validationRunStore;
    private final RpkiRepositoryStore rpkiRepositoryStore;
    private final RpkiObjectStore rpkiObjectStore;
    private final TrustAnchorStore trustAnchorStore;
    private final ValidationScheduler validationScheduler;
    private final Lmdb lmdb;

    @Autowired
    public RpkiRepositoryValidationService(
            ValidationRunStore validationRunStore,
            RpkiRepositoryStore rpkiRepositoryStore,
            RpkiObjectStore rpkiObjectStore,
            RrdpService rrdpService,
            TrustAnchorStore trustAnchorStore,
            Lmdb lmdb,
            @Value("${rpki.validator.rsync.local.storage.directory}") File rsyncLocalStorageDirectory,
            @Value("${rpki.validator.rsync.repository.download.interval}") String rsyncRepositoryDownloadInterval,
            ValidationScheduler validationScheduler) {
        this.validationRunStore = validationRunStore;
        this.rpkiRepositoryStore = rpkiRepositoryStore;
        this.rpkiObjectStore = rpkiObjectStore;
        this.rrdpService = rrdpService;
        this.trustAnchorStore = trustAnchorStore;
        this.rsyncLocalStorageDirectory = rsyncLocalStorageDirectory;
        this.rsyncRepositoryDownloadInterval = Duration.parse(rsyncRepositoryDownloadInterval);
        this.lmdb = lmdb;
        this.validationScheduler = validationScheduler;
    }

    public void validateRpkiRepository(long rpkiRepositoryId) {
        Triple<RpkiRepository, RpkiRepositoryValidationRun, ValidationResult> triple = Tx.with(lmdb.writeTx(), tx -> {
            final Key key = Key.of(rpkiRepositoryId);
            final RpkiRepository rpkiRepository = rpkiRepositoryStore.get(tx, key).orElse(null);
            ValidationResult validationResult = ValidationResult.withLocation(rpkiRepository.getRrdpNotifyUri());
            log.info("Starting RPKI repository validation for " + rpkiRepository);
            Ref<RpkiRepository> rpkiRepositoryRef = rpkiRepositoryStore.makeRef(tx, rpkiRepository.getId());
            final RpkiRepositoryValidationRun validationRun = new RrdpRepositoryValidationRun(rpkiRepositoryRef);
            validationRunStore.add(tx, validationRun);
            return Triple.of(rpkiRepository, validationRun, validationResult);
        });

        final RpkiRepository rpkiRepository = triple.getLeft();
        final RpkiRepositoryValidationRun validationRun = triple.getMiddle();
        final ValidationResult validationResult = triple.getRight();

        final String uri = rpkiRepository.getRrdpNotifyUri();
        if (isRrdpUri(uri)) {
            rrdpService.storeRepository(rpkiRepository, validationRun);
            if (validationRun.isFailed()) {
                rpkiRepository.setFailed();
            } else {
                rpkiRepository.setDownloaded();
            }
        } else if (isRsyncUri(uri)) {
            validationResult.error("rsync.repository.not.supported");
        } else {
            log.error("Unsupported type of the URI " + uri);
        }

        if (validationResult.hasFailures()) {
            validationRun.setFailed();
        } else {
            validationRun.setSucceeded();
        }

        Tx.use(lmdb.writeTx(), tx -> {
            rpkiRepositoryStore.update(tx, rpkiRepository);
            validationRunStore.update(tx, validationRun);
        });

        Tx.ruse(lmdb.readTx(), tx -> {
            if (validationRun.isSucceeded() && validationRun.getAddedObjectCount() > 0) {
                rpkiRepository.getTrustAnchors().forEach(taRef ->
                        trustAnchorStore.get(tx, taRef.key())
                                .ifPresent(validationScheduler::triggerCertificateTreeValidation));
            }
        });
    }

    public void validateRsyncRepositories() {
        Instant cutoffTime = Instant.now().minus(rsyncRepositoryDownloadInterval);
        log.info("updating all rsync repositories that have not been downloaded since {}", cutoffTime);

        Set<TrustAnchor> affectedTrustAnchors = new HashSet<>();

        final RsyncRepositoryValidationRun validationRun = makeRsyncValidationRun();

        final Map<String, RpkiObject> objectsBySha256 = new HashMap<>();
        final Map<URI, RpkiRepository> fetchedLocations = new HashMap<>();

        try {
            ValidationResult results = Tx.rwith(lmdb.readTx(), rpkiRepositoryStore::findRsyncRepositories)
                    .filter(repository -> {
                        boolean needsUpdate = repository.isPending() || repository.getLastDownloadedAt() == null || repository.getLastDownloadedAt().isBefore(cutoffTime);
                        if (!needsUpdate) {
                            fetchedLocations.put(URI.create(repository.getRsyncRepositoryUri()), repository);
                        }
                        return needsUpdate;
                    }).map(repository -> {
                                Tx.use(lmdb.writeTx(), tx -> validationRunStore.associate(tx, validationRun, repository));
                                return processRsyncRepository(affectedTrustAnchors, validationRun, fetchedLocations, objectsBySha256, repository);
                            }
                    ).collect(
                            () -> ValidationResult.withLocation("placeholder"),
                            ValidationResult::addAll,
                            ValidationResult::addAll
                    );

            validationRun.completeWith(results);
            affectedTrustAnchors.forEach(ta -> {
                log.info("The following trust anchor was affected, validation will be triggered {}", ta);
                validationScheduler.triggerCertificateTreeValidation(ta);
            });
        } finally {
            Tx.use(lmdb.writeTx(), tx -> validationRunStore.add(tx, validationRun));
        }
    }

    Set<TrustAnchor> prefetchRepository(RpkiRepository repository) {
        final Set<TrustAnchor> affectedTrustAnchors = new HashSet<>();
        if (repository.isPending() && repository.getType() == RpkiRepository.Type.RSYNC_PREFETCH) {
            log.info("Processing rsync-prefetch repository {}", repository);

            final RsyncRepositoryValidationRun validationRun = makeRsyncValidationRun();

            final ValidationResult validationResult = ValidationResult.withLocation(URI.create(repository.getRsyncRepositoryUri()));
            Tx.use(lmdb.writeTx(), tx -> validationRunStore.associate(tx, validationRun, repository));

            final Map<String, RpkiObject> objectsBySha256 = new HashMap<>();
            try {
                final File targetDirectory = Rsync.localFileFromRsyncUri(rsyncLocalStorageDirectory, URI.create(repository.getRsyncRepositoryUri()));
                fetchRsyncRepository(repository, targetDirectory, validationResult);

                log.info("Storing objects downloaded for {}", repository.getLocationUri());
                storeObjects(targetDirectory, validationRun, validationResult, objectsBySha256, repository);
                log.info("Stored {} objects from the repository {}", objectsBySha256.size(), repository);
                repository.setDownloaded();
            } catch (IOException e) {
                repository.setFailed();
                validationResult.error(ErrorCodes.RSYNC_REPOSITORY_IO, e.toString(), ExceptionUtils.getStackTrace(e));
            } finally {
                Tx.use(lmdb.writeTx(), tx -> validationRunStore.add(tx, validationRun));
            }
            Tx.ruse(lmdb.readTx(), tx ->
                    repository.getTrustAnchors().forEach(taRef ->
                            trustAnchorStore.get(tx, taRef.key()).ifPresent(affectedTrustAnchors::add)));
        }
        return affectedTrustAnchors;
    }

    // TODO Be smarter and don't create a long writing transaction or at
    //  least do the FS walking outside of the writing transaction to make it faster
    private ValidationResult processRsyncRepository(Set<TrustAnchor> affectedTrustAnchors,
                                                    RsyncRepositoryValidationRun validationRun,
                                                    Map<URI, RpkiRepository> fetchedLocations,
                                                    Map<String, RpkiObject> objectsBySha256,
                                                    RpkiRepository repository) {

        log.debug("Processing rsync repository {}", repository);
        final ValidationResult validationResult = ValidationResult.withLocation(URI.create(repository.getRsyncRepositoryUri()));

        try {
            File targetDirectory = Rsync.localFileFromRsyncUri(
                    rsyncLocalStorageDirectory,
                    URI.create(repository.getRsyncRepositoryUri())
            );

            RpkiRepository parentRepository = findDownloadedParentRepository(fetchedLocations, repository);
            if (parentRepository == null) {
                fetchRsyncRepository(repository, targetDirectory, validationResult);
                if (validationResult.hasFailureForCurrentLocation()) {
                    return validationResult;
                }
            }

            if (repository.getType() == RpkiRepository.Type.RSYNC_PREFETCH ||
                    repository.getType() == RpkiRepository.Type.RSYNC &&
                            (parentRepository == null || parentRepository.getType() == RpkiRepository.Type.RSYNC_PREFETCH)) {
                log.info("Storing objects downloaded for {}", repository.getLocationUri());
                storeObjects(targetDirectory, validationRun, validationResult, objectsBySha256, repository);
                log.info("Stored {} objects from the repository {}", objectsBySha256.size(), repository);
                repository.setDownloaded();
            } else {
                log.info("Not storing any objects for the repository {} because parent repository is {}",
                        repository.getLocationUri(), parentRepository == null ? null : parentRepository.getType());
            }
        } catch (IOException e) {
            repository.setFailed();
            validationResult.error(ErrorCodes.RSYNC_REPOSITORY_IO, e.toString(), ExceptionUtils.getStackTrace(e));
        }

        Tx.ruse(lmdb.readTx(), tx ->
                repository.getTrustAnchors().forEach(taRef ->
                        trustAnchorStore.get(tx, taRef.key()).ifPresent(affectedTrustAnchors::add)));
        fetchedLocations.put(URI.create(repository.getRsyncRepositoryUri()), repository);

        return validationResult;
    }

    private RpkiRepository findDownloadedParentRepository(Map<URI, RpkiRepository> fetchedLocations, RpkiRepository repository) {
        URI location = URI.create(repository.getRsyncRepositoryUri());
        for (URI parentLocation : Rsync.generateCandidateParentUris(location)) {
            RpkiRepository parentRepository = fetchedLocations.get(parentLocation);
            if (parentRepository != null) {
                // TODO Fix it
//                repository.setParentRepository(parentRepository);
                if (parentRepository.isDownloaded()) {
                    log.debug("Already fetched {} as part of {}, skipping", repository.getLocationUri(), parentRepository.getLocationUri());
                    repository.setDownloaded(parentRepository.getLastDownloadedAt());
                    return parentRepository;
                }
            }
        }
        return null;
    }

    private void storeObjects(File targetDirectory,
                                RsyncRepositoryValidationRun validationRun,
                                ValidationResult validationResult,
                                Map<String, RpkiObject> objectsBySha256,
                                RpkiRepository repository){
        Tx.use(lmdb.writeTx(), tx -> {
            try {
                traverseFSandStore(tx, targetDirectory, validationRun, validationResult, objectsBySha256, repository);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private void traverseFSandStore(Tx.Write tx,
                                    File targetDirectory,
                                    RsyncRepositoryValidationRun validationRun,
                                    ValidationResult validationResult,
                                    Map<String, RpkiObject> objectsBySha256,
                                    RpkiRepository repository) throws IOException {

        Files.walkFileTree(targetDirectory.toPath(), new SimpleFileVisitor<Path>() {
            private URI currentLocation = URI.create(repository.getLocationUri());

            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                if (dir.equals(targetDirectory.toPath())) {
                    return FileVisitResult.CONTINUE;
                }

                log.trace("visiting {}", dir);

                super.preVisitDirectory(dir, attrs);
                currentLocation = currentLocation.resolve(dir.getFileName().toString() + "/");
                validationResult.setLocation(new ValidationLocation(currentLocation));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                if (dir.equals(targetDirectory.toPath())) {
                    return FileVisitResult.CONTINUE;
                }

                log.trace("leaving {}", dir);
                super.postVisitDirectory(dir, exc);
                currentLocation = currentLocation.resolve("..").normalize();
                validationResult.setLocation(new ValidationLocation(currentLocation));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                super.visitFile(file, attrs);

                validationResult.setLocation(new ValidationLocation(currentLocation.resolve(file.getFileName().toString())));

                byte[] content = Files.readAllBytes(file);
                byte[] sha256 = Sha256.hash(content);

                objectsBySha256.compute(Hex.format(sha256), (key, existing) -> {
                    if (existing == null) {
                        existing = rpkiObjectStore.findBySha256(tx, sha256).orElse(null);
                    }
                    if (existing != null) {
                        existing.addLocation(validationResult.getCurrentLocation().getName());
                        return existing;
                    } else {
                        CertificateRepositoryObject obj = CertificateRepositoryObjectFactory.createCertificateRepositoryObject(content, validationResult);
                        validationRun.addChecks(validationResult);

                        if (validationResult.hasFailureForCurrentLocation()) {
                            log.debug("parsing {} failed: {}", file, validationResult.getFailuresForCurrentLocation());
                            return null;
                        }

                        final RpkiObject object = new RpkiObject(validationResult.getCurrentLocation().getName(), obj);
                        rpkiObjectStore.put(tx, object);
                        validationRunStore.associate(tx, validationRun, object);
                        return object;
                    }
                });

                return FileVisitResult.CONTINUE;
            }
        });
    }

    private RsyncRepositoryValidationRun makeRsyncValidationRun() {
        return Tx.with(lmdb.writeTx(), tx -> validationRunStore.add(tx, new RsyncRepositoryValidationRun()));
    }

    private boolean isRrdpUri(final String uri) {
        return uri.toLowerCase(Locale.ROOT).startsWith("https://") || uri.toLowerCase(Locale.ROOT).startsWith("http://");
    }

    private boolean isRsyncUri(final String uri) {
        return uri.toLowerCase(Locale.ROOT).startsWith("rsync://");
    }

    private void fetchRsyncRepository(RpkiRepository rpkiRepository, File targetDirectory, ValidationResult validationResult) throws IOException {
        if (targetDirectory.mkdirs()) {
            log.info("created local rsync storage directory {} for repository {}", targetDirectory, rpkiRepository);
        }

        net.ripe.rpki.commons.rsync.Rsync rsync = new net.ripe.rpki.commons.rsync.Rsync(rpkiRepository.getLocationUri(), targetDirectory.getPath());
        rsync.addOptions("--update", "--times", "--copy-links", "--recursive", "--delete");
        int exitStatus = rsync.execute();
        validationResult.rejectIfTrue(exitStatus != 0, ErrorCodes.RSYNC_FETCH, String.valueOf(exitStatus), ArrayUtils.toString(rsync.getErrorLines()));
        if (validationResult.hasFailureForCurrentLocation()) {
            rpkiRepository.setFailed();
        } else {
            log.info("Downloaded repository {} to {}", rpkiRepository.getRsyncRepositoryUri(), targetDirectory);
        }
    }
}
