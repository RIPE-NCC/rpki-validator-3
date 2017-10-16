package net.ripe.rpki.validator3.api.validationruns;

import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.validator3.api.Api;
import net.ripe.rpki.validator3.api.ApiResponse;
import net.ripe.rpki.validator3.domain.ValidationRun;
import net.ripe.rpki.validator3.domain.ValidationRunRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.Links;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

import static org.springframework.hateoas.mvc.ControllerLinkBuilder.linkTo;
import static org.springframework.hateoas.mvc.ControllerLinkBuilder.methodOn;

@RestController
@RequestMapping(path = "/validation-runs", produces = Api.API_MIME_TYPE)
@Slf4j
public class ValidationRunController {

    private final ValidationRunRepository validationRunRepository;

    @Autowired
    public ValidationRunController(ValidationRunRepository validationRunRepository) {
        this.validationRunRepository = validationRunRepository;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ValidationRunResource>>> list() {
        return ResponseEntity.ok(ApiResponse.data(
            new Links(linkTo(methodOn(ValidationRunController.class).list()).withSelfRel()),
            validationRunRepository.findAll()
                .stream()
                .map(ValidationRunResource::of)
                .collect(Collectors.toList())
        ));
    }

    @GetMapping(path = "/{id}")
    public ResponseEntity<ApiResponse<ValidationRunResource>> get(@PathVariable long id) {
        ValidationRun validationRun = validationRunRepository.get(id);
        return ResponseEntity.ok(ApiResponse.data(ValidationRunResource.of(validationRun)));
    }
}
