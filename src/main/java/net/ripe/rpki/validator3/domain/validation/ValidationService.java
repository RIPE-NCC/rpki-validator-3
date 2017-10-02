package net.ripe.rpki.validator3.domain.validation;

import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.validator3.domain.TrustAnchor;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class ValidationService {

    public void validate(TrustAnchor trustAnchor) {
        log.info("trust anchor {} located at {} with subject public key info {}", trustAnchor.getName(), trustAnchor.getLocations(), trustAnchor.getSubjectPublicKeyInfo());
    }
}
