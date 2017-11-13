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
import net.ripe.rpki.validator3.util.Sha256;
import org.apache.commons.lang3.ArrayUtils;
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
    private final File localRsyncStorageDirectory;

    @Autowired
    public RpkiRepositoryValidationService(
        EntityManager entityManager,
        ValidationRuns validationRunRepository,
        RpkiRepositories rpkiRepositories,
        RpkiObjects rpkiObjects,
        RrdpService rrdpService,
        @Value("${rpki.validator.local.rsync.storage.directory}") File localRsyncStorageDirectory) {
        this.entityManager = entityManager;
        this.validationRunRepository = validationRunRepository;
        this.rpkiRepositories = rpkiRepositories;
        this.rpkiObjects = rpkiObjects;
        this.rrdpService = rrdpService;
        this.localRsyncStorageDirectory = localRsyncStorageDirectory;
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

    @Scheduled(initialDelay = 60_000, fixedDelay = 60_000)
    public void validateRsyncRepositories() {
        entityManager.setFlushMode(FlushModeType.COMMIT);

        Instant cutoffTime = Instant.now().minus(Duration.ofMinutes(10));
        Set<TrustAnchor> affectedTrustAnchors = new HashSet<>();

        final RsyncRepositoryValidationRun validationRun = new RsyncRepositoryValidationRun();
        validationRunRepository.add(validationRun);

        Stream<RpkiRepository> repositories = rpkiRepositories.findRsyncRepositories();

        Map<URI, RpkiRepository.Status> fetchedLocations = new HashMap<>();
        ValidationResult results = repositories.map((repository) -> {
            URI location = URI.create(repository.getRsyncRepositoryUri());
            ValidationResult validationResult = ValidationResult.withLocation(location);

            if (repository.getLastDownloadedAt() != null && repository.getLastDownloadedAt().isBefore(cutoffTime)) {
                return validationResult;
            }

            validationRun.getRpkiRepositories().add(repository);

            while (!"/".equals(location.getPath())) {
                if (fetchedLocations.containsKey(location)) {
                    log.info("Already fetched {} as part of {}, skipping", repository.getLocationUri(), location);
                    switch (fetchedLocations.get(location)) {
                        case PENDING:
                            break;
                        case FAILED:
                            repository.setFailed();
                            break;
                        case DOWNLOADED:
                            repository.setDownloaded();
                            break;
                    }
                    return validationResult;
                }

                location = location.resolve("..").normalize();
            }

            try {
                File targetDirectory = fetchRsyncRepository(repository, validationResult);
                if (targetDirectory == null || validationResult.hasFailureForCurrentLocation()) {
                    return validationResult;
                }

                Files.walkFileTree(targetDirectory.toPath(), new SimpleFileVisitor<Path>() {
                    private URI currentLocation = URI.create(repository.getLocationUri());

                    @Override
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                        if (dir.equals(targetDirectory.toPath())) {
                            return FileVisitResult.CONTINUE;
                        }

                        log.debug("visiting {}", dir);

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

                        log.debug("leaving {}", dir);
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
                        rpkiObjects.findBySha256(Sha256.hash(content)).map(existing -> {
                            existing.addLocation(validationResult.getCurrentLocation().getName());
                            return existing;
                        }).orElseGet(() -> {
                            log.debug("parsing {}", file);
                            CertificateRepositoryObject obj = CertificateRepositoryObjectFactory.createCertificateRepositoryObject(content, validationResult);
                            validationRun.addChecks(validationResult);

                            if (validationResult.hasFailureForCurrentLocation()) {
                                return null;
                            }

                            RpkiObject object = new RpkiObject(validationResult.getCurrentLocation().getName(), obj);
                            rpkiObjects.add(object);
                            validationRun.addRpkiObject(object);
                            log.debug("added to database {}", object);
                            return object;
                        });

                        return FileVisitResult.CONTINUE;
                    }
                });

                affectedTrustAnchors.addAll(repository.getTrustAnchors());
                repository.setDownloaded();
                fetchedLocations.put(URI.create(repository.getRsyncRepositoryUri()), repository.getStatus());
            } catch (IOException e) {
                e.printStackTrace();
            }

            return validationResult;
        }).collect(
            () -> ValidationResult.withLocation("placeholder"),
            ValidationResult::addAll,
            ValidationResult::addAll
        );

        validationRun.completeWith(results);
        affectedTrustAnchors.forEach(validationRunRepository::runCertificateTreeValidation);
    }

    private boolean isRrdpUri(final String uri) {
        return uri.toLowerCase(Locale.ROOT).startsWith("https://") || uri.toLowerCase(Locale.ROOT).startsWith("http://");
    }

    private boolean isRsyncUri(final String uri) {
        return uri.toLowerCase(Locale.ROOT).startsWith("rsync://");
    }

    private File fetchRsyncRepository(RpkiRepository rpkiRepository, ValidationResult validationResult) throws IOException {
        URI location = URI.create(rpkiRepository.getLocationUri()).normalize();
        File trustAnchorDirectory = new File(localRsyncStorageDirectory, String.valueOf(rpkiRepository.getId()));
        String host = location.getHost() + "/" + (location.getPort() < 0 ? TrustAnchorValidationService.DEFAULT_RSYNC_PORT : location.getPort());
        File targetDirectory = new File(
            new File(trustAnchorDirectory.getCanonicalFile(), host),
            location.getRawPath()
        ).getCanonicalFile();

        if (targetDirectory.mkdirs()) {
            log.info("created local rsync storage directory {} for repository {}", targetDirectory, rpkiRepository);
        }

        Rsync rsync = new Rsync(location.toASCIIString(), targetDirectory.getPath());
        rsync.addOptions("--update", "--times", "--copy-links", "--recursive", "--delete");
        int exitStatus = rsync.execute();
        if (exitStatus != 0) {
            validationResult.error("rsync.error", String.valueOf(exitStatus), ArrayUtils.toString(rsync.getErrorLines()));
            return null;
        } else {
            log.info("Downloaded repository {} to {}", rpkiRepository, targetDirectory);
            return targetDirectory;
        }
    }

}
