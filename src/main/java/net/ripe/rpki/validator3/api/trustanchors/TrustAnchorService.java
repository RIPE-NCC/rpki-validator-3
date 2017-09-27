package net.ripe.rpki.validator3.api.trustanchors;

import net.ripe.rpki.validator3.domain.TrustAnchor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import javax.validation.Valid;

@Component
@Transactional
@Validated
public class TrustAnchorService {
    private final EntityManager entityManager;

    @Autowired
    public TrustAnchorService(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public long execute(@Valid AddTrustAnchor command) {
        TrustAnchor trustAnchor = new TrustAnchor();
        trustAnchor.setName(command.getName());
        trustAnchor.setLocations(command.getLocations());
        trustAnchor.setSubjectPublicKeyInfo(command.getSubjectPublicKeyInfo());
        entityManager.persist(trustAnchor);
        return trustAnchor.getId();
    }
}
