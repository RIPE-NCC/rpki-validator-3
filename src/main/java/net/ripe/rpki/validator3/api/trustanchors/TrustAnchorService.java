package net.ripe.rpki.validator3.api.trustanchors;

import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.validator3.domain.TrustAnchor;
import net.ripe.rpki.validator3.domain.TrustAnchorRepository;
import net.ripe.rpki.validator3.domain.ValidationRunRepository;
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
    private final TrustAnchorRepository trustAnchorRepository;
    private final ValidationRunRepository validationRunRepository;

    @Autowired
    public TrustAnchorService(TrustAnchorRepository trustAnchorRepository, ValidationRunRepository validationRunRepository) {
        this.trustAnchorRepository = trustAnchorRepository;
        this.validationRunRepository = validationRunRepository;
    }

    public long execute(@Valid AddTrustAnchor command) {
        TrustAnchor trustAnchor = new TrustAnchor();
        trustAnchor.setName(command.getName());
        trustAnchor.setLocations(command.getLocations());
        trustAnchor.setSubjectPublicKeyInfo(command.getSubjectPublicKeyInfo());

        trustAnchorRepository.add(trustAnchor);

        return trustAnchor.getId();
    }

    public void remove(long trustAnchorId) {
        TrustAnchor trustAnchor = trustAnchorRepository.get(trustAnchorId);
        validationRunRepository.removeAllForTrustAnchor(trustAnchor);
        trustAnchorRepository.remove(trustAnchor);
    }
}
