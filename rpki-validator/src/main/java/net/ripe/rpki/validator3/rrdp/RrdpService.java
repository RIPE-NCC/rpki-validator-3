package net.ripe.rpki.validator3.rrdp;

import net.ripe.rpki.validator3.storage.data.RpkiRepository;
import net.ripe.rpki.validator3.storage.data.validation.RpkiRepositoryValidationRun;

public interface RrdpService {
    void storeRepository(RpkiRepository rpkiRepository, RpkiRepositoryValidationRun validationRun);
}
