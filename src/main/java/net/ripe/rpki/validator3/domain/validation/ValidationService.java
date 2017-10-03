package net.ripe.rpki.validator3.domain.validation;

import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.commons.rsync.CommandExecutionException;
import net.ripe.rpki.commons.rsync.Rsync;
import net.ripe.rpki.validator3.domain.TrustAnchor;
import net.ripe.rpki.validator3.domain.TrustAnchorRepository;
import net.ripe.rpki.validator3.domain.ValidationRun;
import net.ripe.rpki.validator3.domain.ValidationRunRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.io.File;

@Service
@Slf4j
public class ValidationService {

    private final TrustAnchorRepository trustAnchorRepository;
    private final ValidationRunRepository validationRunRepository;
    private final File localRsyncStorageDirectory;

    public ValidationService(
        TrustAnchorRepository trustAnchorRepository,
        ValidationRunRepository validationRunRepository,
        @Value("${rpki.validator.local.rsync.storage.directory}") File localRsyncStorageDirectory
    ) {
        this.trustAnchorRepository = trustAnchorRepository;
        this.validationRunRepository = validationRunRepository;
        this.localRsyncStorageDirectory = localRsyncStorageDirectory;
    }

    @Transactional(Transactional.TxType.REQUIRED)
    public void validate(long trustAnchorId) {
        TrustAnchor trustAnchor = trustAnchorRepository.get(trustAnchorId);

        log.debug("trust anchor {} located at {} with subject public key info {}", trustAnchor.getName(), trustAnchor.getLocations(), trustAnchor.getSubjectPublicKeyInfo());

        ValidationRun validationRun = new ValidationRun(trustAnchor);
        validationRunRepository.add(validationRun);

        File trustAnchorDirectory = new File(localRsyncStorageDirectory, String.valueOf(trustAnchor.getId()));

        if (trustAnchorDirectory.mkdirs()) {
            log.info("created local rsync storage directory {} for trust anchor {}", localRsyncStorageDirectory, trustAnchor);
        }

        Rsync rsync = new Rsync(validationRun.getTrustAnchorCertificateURI(), trustAnchorDirectory.getAbsolutePath());
        try {
            rsync.execute();
        } catch (CommandExecutionException e) {

        }
    }
}
