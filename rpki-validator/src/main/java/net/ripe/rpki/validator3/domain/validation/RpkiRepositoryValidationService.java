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
import net.ripe.rpki.validator3.domain.ErrorCodes;
import net.ripe.rpki.validator3.domain.RpkiObject;
import net.ripe.rpki.validator3.domain.RpkiObjects;
import net.ripe.rpki.validator3.domain.RpkiRepositories;
import net.ripe.rpki.validator3.domain.RpkiRepository;
import net.ripe.rpki.validator3.domain.RpkiRepositoryValidationRun;
import net.ripe.rpki.validator3.domain.RrdpRepositoryValidationRun;
import net.ripe.rpki.validator3.domain.RsyncRepositoryValidationRun;
import net.ripe.rpki.validator3.domain.TrustAnchor;
import net.ripe.rpki.validator3.domain.ValidationRuns;
import net.ripe.rpki.validator3.rrdp.RrdpService;
import net.ripe.rpki.validator3.util.Hex;
import net.ripe.rpki.validator3.util.Rsync;
import net.ripe.rpki.validator3.util.Sha256;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.persistence.EntityManager;
import javax.persistence.FlushModeType;
import javax.transaction.Transactional;
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
import java.util.stream.Collectors;

@Service
@Slf4j
@Transactional(Transactional.TxType.REQUIRED)
public class RpkiRepositoryValidationService {

    private final EntityManager entityManager;
    private final ValidationRuns validationRunRepository;
    private final RpkiRepositories rpkiRepositories;
    private final RpkiObjects rpkiObjects;
    private final RrdpService rrdpService;
    private final File rsyncLocalStorageDirectory;
    private final Duration rsyncRepositoryDownloadInterval;

    @Autowired
    public RpkiRepositoryValidationService(
        EntityManager entityManager,
        ValidationRuns validationRunRepository,
        RpkiRepositories rpkiRepositories,
        RpkiObjects rpkiObjects,
        RrdpService rrdpService,
        @Value("${rpki.validator.rsync.local.storage.directory}") File rsyncLocalStorageDirectory,
        @Value("${rpki.validator.rsync.repository.download.interval}") String rsyncRepositoryDownloadInterval) {
        this.entityManager = entityManager;
        this.validationRunRepository = validationRunRepository;
        this.rpkiRepositories = rpkiRepositories;
        this.rpkiObjects = rpkiObjects;
        this.rrdpService = rrdpService;
        this.rsyncLocalStorageDirectory = rsyncLocalStorageDirectory;
        this.rsyncRepositoryDownloadInterval = Duration.parse(rsyncRepositoryDownloadInterval);
    }

    public void validateRRDPRepository(long rpkiRepositoryId) {
        entityManager.setFlushMode(FlushModeType.COMMIT);

        final RpkiRepository rpkiRepository = rpkiRepositories.get(rpkiRepositoryId);
        log.info("Starting repository validation for RRDP {} serial {} ", rpkiRepository.getRrdpNotifyUri(),
                rpkiRepository.getRrdpSerial());

        ValidationResult validationResult = ValidationResult.withLocation(rpkiRepository.getRrdpNotifyUri());

        final RpkiRepositoryValidationRun validationRun = new RrdpRepositoryValidationRun(rpkiRepository);
        validationRunRepository.add(validationRun);

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

        if (validationRun.isSucceeded()) {

            Set<TrustAnchor> toBeTriggered = rpkiRepository.getTrustAnchors();
            if(validationRun.getAddedObjectCount() == 0) {
                toBeTriggered = toBeTriggered.stream().filter(t -> !t.isInitialCertificateTreeValidationRunCompleted()).collect(Collectors.toSet());
            }

            if(!toBeTriggered.isEmpty()){
                log.info("Succesful validation of RRDP Repo, kicking tree validation after flush");
                entityManager.flush();
                entityManager.clear();
                log.info("Ready to kick validation tree involving this repo {}", rpkiRepository);
                toBeTriggered.forEach(validationRunRepository::runCertificateTreeValidation);
            }

        }
    }

    public void validateRsyncRepositories() {
        entityManager.setFlushMode(FlushModeType.COMMIT);

        Instant cutoffTime = Instant.now().minus(rsyncRepositoryDownloadInterval);
        log.info("updating all rsync repositories that have not been downloaded since {}", cutoffTime);

        Set<TrustAnchor> affectedTrustAnchors = new HashSet<>();

        final RsyncRepositoryValidationRun validationRun = makeRsyncValidationRun();

        final Map<String, RpkiObject> objectsBySha256 = new HashMap<>();
        final Map<URI, RpkiRepository> fetchedLocations = new HashMap<>();
        ValidationResult results = rpkiRepositories.findRsyncRepositories()
            .filter((repository) -> {
                boolean needsUpdate = repository.isPending() || repository.getLastDownloadedAt() == null || repository.getLastDownloadedAt().isBefore(cutoffTime);
                if (!needsUpdate) {
                    fetchedLocations.put(URI.create(repository.getRsyncRepositoryUri()), repository);
                }
                return needsUpdate;
            })
            .map((repository) -> processRsyncRepository(affectedTrustAnchors, validationRun, fetchedLocations, objectsBySha256, repository))
            .collect(
                () -> ValidationResult.withLocation("placeholder"),
                ValidationResult::addAll,
                ValidationResult::addAll
            );

        validationRun.completeWith(results);
        entityManager.flush();
        entityManager.clear();
        affectedTrustAnchors.forEach(ta -> {
            log.info("The following trust anchor was affected, validation will be triggered {}", ta);
            validationRunRepository.runCertificateTreeValidation(ta);
        });
    }

    public Set<TrustAnchor> prefetchRepository(RpkiRepository repository) {
        entityManager.setFlushMode(FlushModeType.COMMIT);

        final Set<TrustAnchor> affectedTrustAnchors = new HashSet<>();
        if (repository.isPending() && repository.getType() == RpkiRepository.Type.RSYNC_PREFETCH) {
            log.info("Processing rsync-prefetch repository {}", repository);

            final RsyncRepositoryValidationRun validationRun = makeRsyncValidationRun();

            final ValidationResult validationResult = ValidationResult.withLocation(URI.create(repository.getRsyncRepositoryUri()));
            validationRun.addRpkiRepository(repository);

            final Map<String, RpkiObject> objectsBySha256 = new HashMap<>();
            try {
                final File targetDirectory = Rsync.localFileFromRsyncUri(rsyncLocalStorageDirectory, URI.create(repository.getRsyncRepositoryUri()));
                fetchRsyncRepository(repository, targetDirectory, validationResult);

                log.info("Storing objects downloaded for {}", repository.getLocationUri());
                storeObjects(targetDirectory, validationRun, validationResult, objectsBySha256, repository);
                log.info("Stored {} objects from the repository {}", objectsBySha256.size(), repository);
            } catch (IOException e) {
                repository.setFailed();
                validationResult.error(ErrorCodes.RSYNC_REPOSITORY_IO, e.toString(), ExceptionUtils.getStackTrace(e));
            }

            affectedTrustAnchors.addAll(repository.getTrustAnchors());
            repository.setDownloaded();
        }
        return affectedTrustAnchors;
    }

    private ValidationResult processRsyncRepository(Set<TrustAnchor> affectedTrustAnchors,
                                                      RsyncRepositoryValidationRun validationRun,
                                                      Map<URI, RpkiRepository> fetchedLocations,
                                                      Map<String, RpkiObject> objectsBySha256,
                                                      RpkiRepository repository) {

        log.debug("Processing rsync repository {}", repository);
        final ValidationResult validationResult = ValidationResult.withLocation(URI.create(repository.getRsyncRepositoryUri()));

        validationRun.addRpkiRepository(repository);

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
            } else {
                log.info("Not storing any objects for the repository {} because parent repository is {}",
                        repository.getLocationUri(), parentRepository == null ? null : parentRepository.getType());
            }
        } catch (IOException e) {
            repository.setFailed();
            validationResult.error(ErrorCodes.RSYNC_REPOSITORY_IO, e.toString(), ExceptionUtils.getStackTrace(e));
        }

        affectedTrustAnchors.addAll(repository.getTrustAnchors());
        repository.setDownloaded();
        fetchedLocations.put(URI.create(repository.getRsyncRepositoryUri()), repository);

        return validationResult;
    }

    private RpkiRepository findDownloadedParentRepository(Map<URI, RpkiRepository> fetchedLocations, RpkiRepository repository) {
        URI location = URI.create(repository.getRsyncRepositoryUri());
        for (URI parentLocation : Rsync.generateCandidateParentUris(location)) {
            RpkiRepository parentRepository = fetchedLocations.get(parentLocation);
            if (parentRepository != null) {
                repository.setParentRepository(parentRepository);
                if (parentRepository.isDownloaded()) {
                    log.debug("Already fetched {} as part of {}, skipping", repository.getLocationUri(), parentRepository.getLocationUri());
                    repository.setDownloaded(parentRepository.getLastDownloadedAt());
                    return parentRepository;
                }
            }
        }
        return null;
    }

    protected void storeObjects(File targetDirectory,
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
                        existing = rpkiObjects.findBySha256(sha256).orElse(null);
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

                        RpkiObject object = new RpkiObject(validationResult.getCurrentLocation().getName(), obj);
                        rpkiObjects.add(object);
                        validationRun.addRpkiObject(object);
                        return object;
                    }
                });

                return FileVisitResult.CONTINUE;
            }
        });
    }

    private RsyncRepositoryValidationRun makeRsyncValidationRun() {
        final RsyncRepositoryValidationRun validationRun = new RsyncRepositoryValidationRun();
        validationRunRepository.add(validationRun);
        return validationRun;
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
