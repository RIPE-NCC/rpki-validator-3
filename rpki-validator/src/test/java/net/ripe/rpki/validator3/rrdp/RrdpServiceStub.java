package net.ripe.rpki.validator3.rrdp;

import net.ripe.rpki.validator3.storage.data.RpkiRepository;
import net.ripe.rpki.validator3.storage.data.validation.RpkiRepositoryValidationRun;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("test")
public class RrdpServiceStub implements RrdpService {
    @Override
    public void storeRepository(RpkiRepository rpkiRepository, RpkiRepositoryValidationRun validationRun) {

    }
}
