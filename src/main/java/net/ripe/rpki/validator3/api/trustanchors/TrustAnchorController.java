package net.ripe.rpki.validator3.api.trustanchors;

import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.validator3.api.Api;
import net.ripe.rpki.validator3.api.ApiCommand;
import net.ripe.rpki.validator3.api.ApiError;
import net.ripe.rpki.validator3.api.ApiResponse;
import net.ripe.rpki.validator3.api.validationruns.ValidationRunController;
import net.ripe.rpki.validator3.api.validationruns.ValidationRunResource;
import net.ripe.rpki.validator3.domain.TrustAnchor;
import net.ripe.rpki.validator3.domain.TrustAnchorRepository;
import net.ripe.rpki.validator3.domain.ValidationRun;
import net.ripe.rpki.validator3.domain.ValidationRunRepository;
import net.ripe.rpki.validator3.util.TrustAnchorExtractorException;
import net.ripe.rpki.validator3.util.TrustAnchorLocator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.Links;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.Valid;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.springframework.hateoas.mvc.ControllerLinkBuilder.linkTo;
import static org.springframework.hateoas.mvc.ControllerLinkBuilder.methodOn;

@RestController
@RequestMapping(path = "/trust-anchors", produces = Api.API_MIME_TYPE)
@Slf4j
public class TrustAnchorController {

    private final TrustAnchorRepository trustAnchorRepository;
    private final TrustAnchorService trustAnchorService;
    private final ValidationRunRepository validationRunRepository;

    @Autowired
    public TrustAnchorController(TrustAnchorRepository trustAnchorRepository, TrustAnchorService trustAnchorService, ValidationRunRepository validationRunRepository) {
        this.trustAnchorRepository = trustAnchorRepository;
        this.trustAnchorService = trustAnchorService;
        this.validationRunRepository = validationRunRepository;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<TrustAnchorResource>>> list() {
        return ResponseEntity.ok(ApiResponse.data(
            new Links(linkTo(methodOn(TrustAnchorController.class).list()).withSelfRel()),
            trustAnchorRepository.findAll()
                .stream()
                .map(ta -> TrustAnchorResource.of(ta, linkTo(methodOn(TrustAnchorController.class).get(ta.getId())).withSelfRel()))
                .collect(Collectors.toList())
        ));
    }

    @PostMapping(consumes = Api.API_MIME_TYPE)
    public ResponseEntity<ApiResponse<TrustAnchorResource>> add(@RequestBody @Valid ApiCommand<AddTrustAnchor> command) {
        long id = trustAnchorService.execute(command.getData());
        TrustAnchor trustAnchor = trustAnchorRepository.get(id);
        Link selfRel = linkTo(methodOn(TrustAnchorController.class).get(id)).withSelfRel();
        return ResponseEntity.created(URI.create(selfRel.getHref())).body(trustAnchorResource(trustAnchor, selfRel));
    }

    @PostMapping(path = "/upload", consumes = "multipart/form-data")
    public ResponseEntity<ApiResponse<TrustAnchorResource>> add(@RequestParam("file") MultipartFile trustAnchorLocator) throws IOException {
        try {
            TrustAnchorLocator locator = TrustAnchorLocator.fromMultipartFile(trustAnchorLocator);
            AddTrustAnchor command = AddTrustAnchor.builder()
                .type(TrustAnchor.TYPE)
                .name(locator.getCaName())
                .locations(locator.getCertificateLocations().stream().map(URI::toASCIIString).collect(Collectors.toList()))
                .subjectPublicKeyInfo(locator.getPublicKeyInfo())
                .build();
            long id = trustAnchorService.execute(command);
            TrustAnchor trustAnchor = trustAnchorRepository.get(id);
            Link selfRel = linkTo(methodOn(TrustAnchorController.class).get(id)).withSelfRel();
            return ResponseEntity.created(URI.create(selfRel.getHref())).body(trustAnchorResource(trustAnchor, selfRel));
        } catch (TrustAnchorExtractorException ex) {
            return ResponseEntity.badRequest().body(ApiResponse.error(ApiError.of(
                HttpStatus.BAD_REQUEST,
                "Invalid trust anchor locator: " + ex.getMessage()
            )));
        }
    }

    @GetMapping(path = "/{id}")
    public ResponseEntity<ApiResponse<TrustAnchorResource>> get(@PathVariable long id) {
        TrustAnchor trustAnchor = trustAnchorRepository.get(id);
        return ResponseEntity.ok(trustAnchorResource(trustAnchor, linkTo(methodOn(TrustAnchorController.class).get(id)).withSelfRel()));
    }

    @DeleteMapping(path = "/{id}")
    public ResponseEntity<?> delete(@PathVariable long id) {
        trustAnchorService.remove(id);
        return ResponseEntity.noContent().build();
    }

    private ApiResponse<TrustAnchorResource> trustAnchorResource(TrustAnchor trustAnchor, Link selfRel) {
        Optional<ValidationRun> validationRun = validationRunRepository.findLatestCompletedForTrustAnchor(trustAnchor);
        ArrayList<Object> includes = new ArrayList<>(1);
        validationRun.ifPresent(run -> includes.add(ValidationRunResource.of(run, linkTo(methodOn(ValidationRunController.class).get(run.getId())).withSelfRel())));
        return ApiResponse.<TrustAnchorResource>builder().data(
            TrustAnchorResource.of(trustAnchor, selfRel)
        ).includes(includes).build();
    }
}
