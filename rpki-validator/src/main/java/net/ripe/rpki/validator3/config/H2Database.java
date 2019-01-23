package net.ripe.rpki.validator3.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.persistence.EntityManager;
import javax.transaction.Transactional;

@Component
@Transactional
@Slf4j
public class H2Database {

    @Autowired
    private EntityManager entityManager;

    public void checkpoint() {
        try {
            entityManager.createNativeQuery("CHECKPOINT SYNC;").executeUpdate();
        } catch (Exception e) {
            log.error("Couldn't execute CHECKPOINT on the H2 database", e);
        }
    }
}
