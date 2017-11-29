package net.ripe.rpki.validator3.adapter.jpa;

import net.ripe.rpki.validator3.domain.Settings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import java.util.Optional;

import static net.ripe.rpki.validator3.adapter.jpa.querydsl.QSetting.setting;

@Component
@Transactional(Transactional.TxType.MANDATORY)
public class JPASettings extends JPARepository<Setting> implements Settings {
    @Autowired
    private EntityManager entityManager;

    protected JPASettings() {
        super(setting);
    }

    @Override
    public Optional<String> get(String key) {
        return Optional.ofNullable(select().where(setting.key.eq(key)).fetchFirst()).map(Setting::getValue);
    }

    @Override
    public void put(String key, String value) {
        Setting existing = select().where(setting.key.eq(key)).fetchFirst();
        if (existing == null) {
            entityManager.persist(new Setting(key, value));
        } else {
            existing.setValue(value);
        }
    }
}
