package net.ripe.rpki.validator3.api.roas;

import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.commons.crypto.cms.roa.RoaCms;
import net.ripe.rpki.commons.validation.ValidationResult;
import net.ripe.rpki.validator3.api.Api;
import net.ripe.rpki.validator3.api.ApiResponse;
import net.ripe.rpki.validator3.domain.RpkiObject;
import net.ripe.rpki.validator3.domain.RpkiObjects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

import static java.util.stream.Collectors.toList;

@RestController
@RequestMapping(path = "/roas", produces = Api.API_MIME_TYPE)
@Slf4j
public class RoaController {
    private final RpkiObjects rpkiObjects;

    @Autowired
    public RoaController(RpkiObjects rpkiObjects) {
        this.rpkiObjects = rpkiObjects;
    }

    @GetMapping(path = "/validated-prefixes")
    public ResponseEntity<ApiResponse<List<ValidatedPrefix>>> list() {
        ValidationResult validationResult = ValidationResult.withLocation("X.roa");
        List<RpkiObject> validatedRoas = rpkiObjects.findCurrentlyValidated(RpkiObject.Type.ROA);
        log.debug("validated ROAs count {}", validatedRoas.size());
        List<ValidatedPrefix> validatedPrefixes = validatedRoas
            .stream().map(x -> x.get(RoaCms.class, validationResult))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .flatMap(ValidatedPrefix::of)
            .collect(toList());
        log.debug("validated prefixes count {}", validatedPrefixes.size());
        return ResponseEntity.ok(ApiResponse.data(validatedPrefixes));
    }
}
