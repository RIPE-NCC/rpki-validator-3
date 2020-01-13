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

import fj.data.Either;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.commons.validation.ValidationLocation;
import net.ripe.rpki.commons.validation.ValidationResult;
import net.ripe.rpki.validator3.background.ValidationScheduler;
import net.ripe.rpki.validator3.domain.ErrorCodes;
import net.ripe.rpki.validator3.domain.RpkiObjectUtils;
import net.ripe.rpki.validator3.rrdp.RrdpService;
import net.ripe.rpki.validator3.storage.Storage;
import net.ripe.rpki.validator3.storage.Tx;
import net.ripe.rpki.validator3.storage.data.Key;
import net.ripe.rpki.validator3.storage.data.Ref;
import net.ripe.rpki.validator3.storage.data.RpkiObject;
import net.ripe.rpki.validator3.storage.data.RpkiRepository;
import net.ripe.rpki.validator3.storage.data.TrustAnchor;
import net.ripe.rpki.validator3.storage.data.validation.RpkiRepositoryValidationRun;
import net.ripe.rpki.validator3.storage.data.validation.RrdpRepositoryValidationRun;
import net.ripe.rpki.validator3.storage.data.validation.RsyncRepositoryValidationRun;
import net.ripe.rpki.validator3.storage.stores.RpkiObjects;
import net.ripe.rpki.validator3.storage.stores.RpkiRepositories;
import net.ripe.rpki.validator3.storage.stores.TrustAnchors;
import net.ripe.rpki.validator3.storage.stores.ValidationRuns;
import net.ripe.rpki.validator3.util.Hex;
import net.ripe.rpki.validator3.util.Rsync;
import net.ripe.rpki.validator3.util.RsyncFactory;
import net.ripe.rpki.validator3.util.Sha256;
import net.ripe.rpki.validator3.util.Time;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.tuple.Pair;
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
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

@Service
@Slf4j
public class RpkiRepositoryValidationService {

    private final RrdpService rrdpService;
    private final File rsyncLocalStorageDirectory;
    private final ValidationRuns validationRuns;
    private final RpkiRepositories rpkiRepositories;
    private final RpkiObjects rpkiObjects;
    private final TrustAnchors trustAnchors;
    private final ValidationScheduler validationScheduler;
    private final Storage storage;
    private final TrustAnchorState trustAnchorState;
    private final RsyncFactory rsyncFactory;

    @Autowired
    public RpkiRepositoryValidationService(
        ValidationRuns validationRuns,
        RpkiRepositories rpkiRepositories,
        RpkiObjects rpkiObjects,
        RrdpService rrdpService,
        TrustAnchors trustAnchors,
        Storage storage,
        @Value("${rpki.validator.rsync.local.storage.directory}") File rsyncLocalStorageDirectory,
        TrustAnchorState trustAnchorState,
        ValidationScheduler validationScheduler, RsyncFactory rsyncFactory) {
        this.validationRuns = validationRuns;
        this.rpkiRepositories = rpkiRepositories;
        this.rpkiObjects = rpkiObjects;
        this.rrdpService = rrdpService;
        this.trustAnchors = trustAnchors;
        this.rsyncLocalStorageDirectory = rsyncLocalStorageDirectory;
        this.storage = storage;
        this.trustAnchorState = trustAnchorState;
        this.validationScheduler = validationScheduler;
        this.rsyncFactory = rsyncFactory;
    }

    public void validateRrdpRpkiRepository(long rpkiRepositoryId) {
        final Key key = Key.of(rpkiRepositoryId);
        final RpkiRepository rpkiRepository = storage.readTx(tx -> rpkiRepositories.get(tx, key).orElse(null));
        if (rpkiRepository == null) {
            log.info("RPKI repository with key {} doesn't exist ", rpkiRepositoryId);
            return;
        }
        log.info("Starting RPKI repository validation for " + rpkiRepository);
        final ValidationResult validationResult = ValidationResult.withLocation(rpkiRepository.getRrdpNotifyUri());

        final RpkiRepositoryValidationRun validationRun = storage.writeTx(tx -> {
            Ref<RpkiRepository> rpkiRepositoryRef = rpkiRepositories.makeRef(tx, rpkiRepository.key());
            RrdpRepositoryValidationRun newVR = validationRuns.add(tx, new RrdpRepositoryValidationRun(rpkiRepositoryRef));
            validationRuns.associate(tx, newVR, rpkiRepository);
            return newVR;
        });

        boolean triggerCaTreeAfter = false;
        boolean changedAtLeastOneObject = false;
        try {
            final String uri = rpkiRepository.getRrdpNotifyUri();
            if (isRrdpUri(uri)) {
                changedAtLeastOneObject = rrdpService.storeRepository(rpkiRepository, validationRun);
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
                triggerCaTreeAfter = true;
            }
        } catch (Exception e) {
            log.error("Error validating repository " + rpkiRepository, e);
            validationRun.setFailed();
        } finally {
            if (triggerCaTreeAfter && changedAtLeastOneObject) {
                storage.readTx0(tx ->
                    rpkiRepository.getTrustAnchors().forEach(taRef ->
                        trustAnchors.get(tx, taRef.key()).ifPresent(trustAnchorState::setUnknown)));
            }
            storage.writeTx0(tx -> {
                rpkiRepositories.update(tx, rpkiRepository);
                validationRuns.update(tx, validationRun);
            });
            if (triggerCaTreeAfter && changedAtLeastOneObject) {
                storage.readTx0(tx ->
                    rpkiRepository.getTrustAnchors().forEach(taRef ->
                        trustAnchors.get(tx, taRef.key())
                            .ifPresent(validationScheduler::triggerCertificateTreeValidation)));
            }
        }
    }

    public void validateRsyncRepositories() {
        Instant cutoffTime = Instant.now().minus(validationScheduler.getRsyncRepositoryDownloadInterval());
        log.info("updating all rsync repositories that have not been downloaded since {}", cutoffTime);

        Set<TrustAnchor> affectedTrustAnchors = new HashSet<>();

        final RsyncRepositoryValidationRun validationRun = makeAndStoreRsyncValidationRun();

        final Set<String> existingObjectKeys = new HashSet<>();
        final Map<URI, RpkiRepository> fetchedLocations = new HashMap<>();

        try {
            Stream<RpkiRepository> repositoriesNeedingUpdate = storage.readTx(rpkiRepositories::findRsyncRepositories)
                .filter(repository -> {
                    boolean needsUpdate = repository.isPending() || repository.getLastDownloadedAt() == null || repository.getLastDownloadedAt().isBefore(cutoffTime);
                    if (!needsUpdate) {
                        fetchedLocations.put(URI.create(repository.getRsyncRepositoryUri()), repository);
                    }
                    return needsUpdate;
                });

            ValidationResult results = repositoriesNeedingUpdate.map(repository -> {
                    storage.writeTx0(tx -> validationRuns.associate(tx, validationRun, repository));
                    return processRsyncRepository(affectedTrustAnchors, validationRun, fetchedLocations, existingObjectKeys, repository);
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
        } catch (Exception e) {
            validationRun.setFailed();
        } finally {
            storage.writeTx0(tx -> validationRuns.update(tx, validationRun));
        }
    }

    Set<TrustAnchor> prefetchRepository(RpkiRepository repository) {
        final Set<TrustAnchor> affectedTrustAnchors = new HashSet<>();
        if (repository.isPending() && repository.getType() == RpkiRepository.Type.RSYNC_PREFETCH) {
            log.info("Processing rsync-prefetch repository {}", repository);

            final RsyncRepositoryValidationRun validationRun = makeAndStoreRsyncValidationRun();

            final ValidationResult validationResult = ValidationResult.withLocation(URI.create(repository.getRsyncRepositoryUri()));
            storage.writeTx0(tx -> validationRuns.associate(tx, validationRun, repository));

            final Set<String> existingObjectsBySha256 = new HashSet<>();
            try {
                final File targetDirectory = Rsync.localFileFromRsyncUri(rsyncLocalStorageDirectory, URI.create(repository.getRsyncRepositoryUri()));
                fetchRsyncRepository(repository, targetDirectory, validationResult);

                log.info("Storing objects downloaded for {}", repository.getLocationUri());
                Long t = Time.timed(() -> storeObjects(targetDirectory, validationRun, validationResult, existingObjectsBySha256, repository));
                log.info("Stored {} objects from the repository {} in {}ms", existingObjectsBySha256.size(), repository, t);
                repository.setDownloaded();
            } catch (IOException e) {
                repository.setFailed();
                validationResult.error(ErrorCodes.RSYNC_REPOSITORY_IO, e.toString(), ExceptionUtils.getStackTrace(e));
            } finally {
                storage.writeTx0(tx -> {
                    rpkiRepositories.update(tx, repository);
                    validationRuns.add(tx, validationRun);
                });
            }
            storage.readTx0(tx ->
                repository.getTrustAnchors().forEach(taRef ->
                    trustAnchors.get(tx, taRef.key()).ifPresent(affectedTrustAnchors::add)));
        }
        return affectedTrustAnchors;
    }

    private ValidationResult processRsyncRepository(Set<TrustAnchor> affectedTrustAnchors,
                                                    RsyncRepositoryValidationRun validationRun,
                                                    Map<URI, RpkiRepository> fetchedLocations,
                                                    Set<String> existingObjectsSha256,
                                                    RpkiRepository repository) {

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
                Long t = Time.timed(() -> storeObjects(targetDirectory, validationRun, validationResult, existingObjectsSha256, repository));
                log.info("Stored {} objects from the repository {} in {}ms", existingObjectsSha256.size(), repository, t);
                repository.setDownloaded();
            }
        } catch (IOException e) {
            repository.setFailed();
            validationResult.error(ErrorCodes.RSYNC_REPOSITORY_IO, e.toString(), ExceptionUtils.getStackTrace(e));
        } finally {
            storage.writeTx0(tx -> rpkiRepositories.update(tx, repository));
        }

        storage.readTx0(tx ->
            repository.getTrustAnchors().forEach(taRef ->
                trustAnchors.get(tx, taRef.key()).ifPresent(ta -> {
                    trustAnchorState.setUnknown(ta);
                    affectedTrustAnchors.add(ta);
                })));

        fetchedLocations.put(URI.create(repository.getRsyncRepositoryUri()), repository);

        return validationResult;
    }

    private RpkiRepository findDownloadedParentRepository(Map<URI, RpkiRepository> fetchedLocations, RpkiRepository repository) {
        URI location = URI.create(repository.getRsyncRepositoryUri());
        for (URI parentLocation : Rsync.generateCandidateParentUris(location)) {
            RpkiRepository parentRepository = fetchedLocations.get(parentLocation);
            if (parentRepository != null) {
                final Ref<RpkiRepository> rpkiRepositoryRef = storage.readTx(tx -> rpkiRepositories.makeRef(tx, parentRepository.key()));
                repository.setParentRepository(rpkiRepositoryRef);
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
                              Set<String> existingObjectsKeys,
                              RpkiRepository repository) {
        storage.writeTx0(tx -> {
            try {
                traverseFSandStore(tx, targetDirectory, validationRun, validationResult, existingObjectsKeys, repository);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private void traverseFSandStore(Tx.Write tx,
                                    File targetDirectory,
                                    RsyncRepositoryValidationRun validationRun,
                                    ValidationResult validationResult,
                                    Set<String> existingObjectsKeys,
                                    RpkiRepository repository) throws IOException {

        final AtomicInteger workCounter = new AtomicInteger(0);
        final int threshold = 10;

        Files.walkFileTree(targetDirectory.toPath(), new SimpleFileVisitor<Path>() {
            private URI currentLocation = URI.create(repository.getLocationUri());

            // Pre and post visit maintains validationResult location to be up to date with actual dir being visited.
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

                final URI objectLocation = currentLocation.resolve(file.getFileName().toString());
                validationResult.setLocation(new ValidationLocation(objectLocation));

                final long objectSize = file.toFile().length();
                if (objectSize > RpkiObject.MAX_SIZE) {
                    validationResult.error(ErrorCodes.REPOSITORY_OBJECT_MAXIMUM_SIZE, objectLocation.toASCIIString(), String.valueOf(objectSize), String.valueOf(RpkiObject.MAX_SIZE));
                } else {
                    final byte[] content = Files.readAllBytes(file);
                    final byte[] sha256 = Sha256.hash(content);

                    final String key = Hex.format(sha256);
                    final String location = validationResult.getCurrentLocation().getName();

                    boolean exists = existingObjectsKeys.contains(key);

                    if (!exists) {
                        if (workCounter.get() > threshold) {
                            storeObject(tx, validationRun, existingObjectsKeys);
                            workCounter.decrementAndGet();
                        }
                        asyncCreateObjects.submit(() -> RpkiObjectUtils.createRpkiObject(location, content));
                        workCounter.incrementAndGet();
                    } else {
                        rpkiObjects.addLocation(tx, Key.of(key), location);
                    }
                }
                return FileVisitResult.CONTINUE;
            }
        });

        while (workCounter.getAndDecrement() > 0) {
            storeObject(tx, validationRun, existingObjectsKeys);
        }
    }

    private void storeObject(Tx.Write tx, RpkiRepositoryValidationRun validationRun,
                             Set<String> existingObjectsKeys) {
        try {
            final Either<ValidationResult, Pair<String, RpkiObject>> maybeRpkiObject = asyncCreateObjects.take().get();
            if (maybeRpkiObject.isLeft()) {
                final ValidationResult value = maybeRpkiObject.left().value();
                validationRun.addChecks(value);
                log.debug("parsing {} failed: {}", value.getCurrentLocation().getName(), value);
            } else {
                final Pair<String, RpkiObject> p = maybeRpkiObject.right().value();
                final RpkiObject object = p.getRight();
                final String key = Hex.format(object.getSha256());
                final String location = p.getLeft();
                if (existingObjectsKeys.contains(key)) {
                    // re-check it for a weird case of object with the
                    // same hash inserted while this task was in the pool
                    rpkiObjects.addLocation(tx, Key.of(key), location);
                } else {
                    rpkiObjects.put(tx, object, location);
                    existingObjectsKeys.add(key);
                }
            }
        } catch (Exception e) {
            log.error("Something strange happened here", e);
        }
    }

    private ExecutorCompletionService<Either<ValidationResult, Pair<String, RpkiObject>>> asyncCreateObjects =
        new ExecutorCompletionService<>(Executors.newFixedThreadPool(Math.max(1, Runtime.getRuntime().availableProcessors() - 1)));

    private RsyncRepositoryValidationRun makeAndStoreRsyncValidationRun() {
        return storage.writeTx(tx -> validationRuns.add(tx, new RsyncRepositoryValidationRun()));
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
        net.ripe.rpki.commons.rsync.Rsync rsync = rsyncFactory.rsyncDirectory(rpkiRepository.getLocationUri(), targetDirectory.getPath());
        int exitStatus = rsync.execute();
        validationResult.rejectIfTrue(exitStatus != 0, ErrorCodes.RSYNC_FETCH, String.valueOf(exitStatus), ArrayUtils.toString(rsync.getErrorLines()));
        if (validationResult.hasFailureForCurrentLocation()) {
            rpkiRepository.setFailed();
        } else {
            log.info("Downloaded repository {} to {}", rpkiRepository.getRsyncRepositoryUri(), targetDirectory);
        }
    }
}
