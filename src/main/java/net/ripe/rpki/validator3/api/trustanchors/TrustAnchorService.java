package net.ripe.rpki.validator3.api.trustanchors;

import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.validator3.domain.RpkiRepositories;
import net.ripe.rpki.validator3.domain.RpkiRepository;
import net.ripe.rpki.validator3.domain.TrustAnchor;
import net.ripe.rpki.validator3.domain.TrustAnchors;
import net.ripe.rpki.validator3.domain.ValidationRuns;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import javax.transaction.Transactional;
import javax.validation.Valid;

@Component
@Transactional
@Validated
@Slf4j
public class TrustAnchorService {
    @Autowired
    private TrustAnchors trustAnchors;
    @Autowired
    private RpkiRepositories rpkiRepositories;
    @Autowired
    private ValidationRuns validationRunRepository;

    public long execute(@Valid AddTrustAnchor command) {
        TrustAnchor trustAnchor = new TrustAnchor();
        trustAnchor.setName(command.getName());
        trustAnchor.setLocations(command.getLocations());
        trustAnchor.setSubjectPublicKeyInfo(command.getSubjectPublicKeyInfo());

        if (command.getRsyncPrefetchUri() != null) {
            trustAnchor.setRsyncPrefetchUri(command.getRsyncPrefetchUri());
            rpkiRepositories.register(trustAnchor, command.getRsyncPrefetchUri(), RpkiRepository.Type.RSYNC_PREFETCH);
        }

        trustAnchors.add(trustAnchor);

        log.info("added trust anchor '{}'", trustAnchor);

        return trustAnchor.getId();
    }

    public void remove(long trustAnchorId) {
        TrustAnchor trustAnchor = trustAnchors.get(trustAnchorId);
        validationRunRepository.removeAllForTrustAnchor(trustAnchor);
        rpkiRepositories.removeAllForTrustAnchor(trustAnchor);
        trustAnchors.remove(trustAnchor);
    }

}
