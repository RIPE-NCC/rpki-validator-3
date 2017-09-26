package net.ripe.rpki.validator3.api.trustanchors;

import net.ripe.rpki.validator3.domain.TrustAnchor;
import net.ripe.rpki.validator3.domain.TrustAnchorRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import javax.transaction.Transactional;
import javax.validation.Valid;

@Component
@Transactional
@Validated
public class TrustAnchorService {
    private final TrustAnchorRepository trustAnchorRepository;

    @Autowired
    public TrustAnchorService(TrustAnchorRepository trustAnchorRepository) {
        this.trustAnchorRepository = trustAnchorRepository;
    }

    public long execute(@Valid AddTrustAnchor command) {
        TrustAnchor trustAnchor = new TrustAnchor();
        trustAnchor.setName(command.getName());
        trustAnchor.setLocations(command.getLocations());
        trustAnchor.setSubjectPublicKeyInfo(command.getSubjectPublicKeyInfo());
        trustAnchorRepository.add(trustAnchor);
        return trustAnchor.getId();
    }
}
