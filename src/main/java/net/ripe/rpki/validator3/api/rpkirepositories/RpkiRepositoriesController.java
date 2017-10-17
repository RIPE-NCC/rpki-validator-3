package net.ripe.rpki.validator3.api.rpkirepositories;

import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.validator3.api.Api;
import net.ripe.rpki.validator3.api.ApiResponse;
import net.ripe.rpki.validator3.api.validationruns.ValidationRunResource;
import net.ripe.rpki.validator3.domain.RpkiRepositories;
import net.ripe.rpki.validator3.domain.RpkiRepository;
import net.ripe.rpki.validator3.domain.ValidationRun;
import net.ripe.rpki.validator3.domain.ValidationRuns;
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
@RequestMapping(path = "/rpki-repositories", produces = Api.API_MIME_TYPE)
@Slf4j
public class RpkiRepositoriesController {

    private final RpkiRepositories rpkiRepositories;

    @Autowired
    public RpkiRepositoriesController(RpkiRepositories rpkiRepositories) {
        this.rpkiRepositories = rpkiRepositories;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<RpkiRepositoryResource>>> list() {
        return ResponseEntity.ok(ApiResponse.data(
            new Links(linkTo(methodOn(RpkiRepositoriesController.class).list()).withSelfRel()),
            rpkiRepositories.findAll()
                .stream()
                .map(RpkiRepositoryResource::of)
                .collect(Collectors.toList())
        ));
    }

    @GetMapping(path = "/{id}")
    public ResponseEntity<ApiResponse<RpkiRepositoryResource>> get(@PathVariable long id) {
        RpkiRepository rpkiRepository = rpkiRepositories.get(id);
        return ResponseEntity.ok(ApiResponse.data(RpkiRepositoryResource.of(rpkiRepository)));
    }
}
