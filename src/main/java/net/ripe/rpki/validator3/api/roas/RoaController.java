package net.ripe.rpki.validator3.api.roas;

import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.validator3.api.Api;
import net.ripe.rpki.validator3.api.ApiResponse;
import net.ripe.rpki.validator3.domain.RpkiObject;
import net.ripe.rpki.validator3.domain.RpkiObjects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.stream.Stream;

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
    public ResponseEntity<ApiResponse<Stream<ValidatedPrefix>>> list() {
        Stream<ValidatedPrefix> validatedPrefixes = rpkiObjects
            .findCurrentlyValidated(RpkiObject.Type.ROA)
            .flatMap(x -> x.getValue().getRoaPrefixes().stream())
            .map(ValidatedPrefix::of)
            .distinct();
        return ResponseEntity.ok(ApiResponse.data(validatedPrefixes));
    }
}
