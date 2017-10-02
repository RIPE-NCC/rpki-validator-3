package net.ripe.rpki.validator3.api.trustanchors;

import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.validator3.domain.TrustAnchor;
import net.ripe.rpki.validator3.domain.validation.ValidationScheduler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import javax.validation.Valid;

@Component
@Transactional
@Validated
@Slf4j
public class TrustAnchorService {
    private final EntityManager entityManager;
    private final ValidationScheduler scheduler;

    @Autowired
    public TrustAnchorService(EntityManager entityManager, ValidationScheduler scheduler) {
        this.entityManager = entityManager;
        this.scheduler = scheduler;
    }

    public long execute(@Valid AddTrustAnchor command) {
        TrustAnchor trustAnchor = new TrustAnchor();
        trustAnchor.setName(command.getName());
        trustAnchor.setLocations(command.getLocations());
        trustAnchor.setSubjectPublicKeyInfo(command.getSubjectPublicKeyInfo());

        entityManager.persist(trustAnchor);
        scheduler.addTrustAnchor(trustAnchor);

        return trustAnchor.getId();
    }
}
