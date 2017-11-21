package net.ripe.rpki.validator3.domain.validation;

import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.commons.crypto.CertificateRepositoryObject;
import net.ripe.rpki.commons.crypto.util.CertificateRepositoryObjectFactory;
import net.ripe.rpki.commons.rsync.Rsync;
import net.ripe.rpki.commons.validation.ValidationLocation;
import net.ripe.rpki.commons.validation.ValidationResult;
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
import net.ripe.rpki.validator3.util.RsyncUtils;
import net.ripe.rpki.validator3.util.Sha256;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
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
import java.util.stream.Stream;

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
    private final String rsyncRepositoryDownloadInterval;

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
        this.rsyncRepositoryDownloadInterval = rsyncRepositoryDownloadInterval;
    }

    public void validateRpkiRepository(long rpkiRepositoryId) {
        entityManager.setFlushMode(FlushModeType.COMMIT);

        final RpkiRepository rpkiRepository = rpkiRepositories.get(rpkiRepositoryId);
        log.info("Starting RPKI repository validation for " + rpkiRepository);

        ValidationResult validationResult = ValidationResult.withLocation(rpkiRepository.getRrdpNotifyUri());

        final RpkiRepositoryValidationRun validationRun = new RrdpRepositoryValidationRun(rpkiRepository);
        validationRunRepository.add(validationRun);

        final String uri = rpkiRepository.getRrdpNotifyUri();
        if (isRrdpUri(uri)) {
            rrdpService.storeRepository(rpkiRepository, validationRun);
            rpkiRepository.setDownloaded();
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

        if (validationRun.isSucceeded() && validationRun.getAddedObjectCount() > 0) {
            rpkiRepository.getTrustAnchors().forEach(trustAnchor -> {
                validationRunRepository.runCertificateTreeValidation(trustAnchor);
            });
        }
    }

    @Scheduled(initialDelay = 10_000, fixedDelay = 60_000)
    public void validateRsyncRepositories() {
        entityManager.setFlushMode(FlushModeType.COMMIT);

        Instant cutoffTime = Instant.now().minus(Duration.parse(rsyncRepositoryDownloadInterval));
        log.info("updating all rsync repositories that have not been downloaded since {}", cutoffTime);

        Set<TrustAnchor> affectedTrustAnchors = new HashSet<>();

        final RsyncRepositoryValidationRun validationRun = new RsyncRepositoryValidationRun();
        validationRunRepository.add(validationRun);

        Stream<RpkiRepository> repositories = rpkiRepositories.findRsyncRepositories();

        Map<String, RpkiObject> objectsBySha256 = new HashMap<>();
        Map<URI, RpkiRepository> fetchedLocations = new HashMap<>();
        ValidationResult results = repositories
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
        affectedTrustAnchors.forEach(validationRunRepository::runCertificateTreeValidation);
    }

    protected ValidationResult processRsyncRepository(Set<TrustAnchor> affectedTrustAnchors, RsyncRepositoryValidationRun validationRun, Map<URI, RpkiRepository> fetchedLocations, Map<String, RpkiObject> objectsBySha256, RpkiRepository repository) {
        ValidationResult validationResult = ValidationResult.withLocation(URI.create(repository.getRsyncRepositoryUri()));

        validationRun.addRpkiRepository(repository);

        try {
            File targetDirectory = RsyncUtils.localFileFromRsyncUri(
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

            if (repository.getType() == RpkiRepository.Type.RSYNC && (parentRepository == null || parentRepository.getType() == RpkiRepository.Type.RSYNC_PREFETCH)) {
                storeObjects(targetDirectory, validationRun, validationResult, objectsBySha256, repository);
            }
        } catch (IOException e) {
            repository.setFailed();
            validationResult.error("rsync.repository.io.error", e.toString(), ExceptionUtils.getStackTrace(e));
        }

        affectedTrustAnchors.addAll(repository.getTrustAnchors());
        repository.setDownloaded();
        fetchedLocations.put(URI.create(repository.getRsyncRepositoryUri()), repository);

        return validationResult;
    }

    private RpkiRepository findDownloadedParentRepository(Map<URI, RpkiRepository> fetchedLocations, RpkiRepository repository) {
        URI location = URI.create(repository.getRsyncRepositoryUri());
        while (!"/".equals(location.getPath())) {
            RpkiRepository parentRepository = fetchedLocations.get(location);
            if (parentRepository != null && parentRepository.isDownloaded()) {
                log.debug("Already fetched {} as part of {}, skipping", repository.getLocationUri(), location);
                repository.setDownloaded(parentRepository.getLastDownloadedAt());
                return parentRepository;
            }
            location = location.resolve("..").normalize();
        }
        return null;
    }

    protected void storeObjects(File targetDirectory, RsyncRepositoryValidationRun validationRun, ValidationResult validationResult, Map<String, RpkiObject> objectsBySha256, RpkiRepository repository) throws IOException {
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

                objectsBySha256.compute(Sha256.format(sha256), (key, existing) -> {
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
                        log.debug("added to database {}", object);
                        return object;
                    }
                });

                return FileVisitResult.CONTINUE;
            }
        });
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

        Rsync rsync = new Rsync(rpkiRepository.getLocationUri(), targetDirectory.getPath());
        rsync.addOptions("--update", "--times", "--copy-links", "--recursive", "--delete");
        int exitStatus = rsync.execute();
        if (exitStatus != 0) {
            validationResult.error("rsync.repository.rsync.error", String.valueOf(exitStatus), ArrayUtils.toString(rsync.getErrorLines()));
            rpkiRepository.setFailed();
        } else {
            log.info("Downloaded repository {} to {}", rpkiRepository.getRsyncRepositoryUri(), targetDirectory);
        }
    }
}
