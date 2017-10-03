package net.ripe.rpki.validator3.adapter.jpa;

import net.ripe.rpki.validator3.domain.ValidationRun;
import net.ripe.rpki.validator3.domain.ValidationRunRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import javax.persistence.EntityManager;
import javax.transaction.Transactional;

@Repository
@Transactional(Transactional.TxType.REQUIRED)
public class JPAValidationRunRepository implements ValidationRunRepository {
    private final EntityManager entityManager;

    @Autowired
    public JPAValidationRunRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public void add(ValidationRun validationRun) {
        entityManager.persist(validationRun);
    }
}
