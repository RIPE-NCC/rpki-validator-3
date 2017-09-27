package net.ripe.rpki.validator3.api.trustanchors;

import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.validator3.api.Api;
import net.ripe.rpki.validator3.api.ApiCommand;
import net.ripe.rpki.validator3.api.ApiResponse;
import net.ripe.rpki.validator3.domain.TrustAnchor;
import net.ripe.rpki.validator3.domain.TrustAnchorRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.Links;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.stream.Collectors;

import static org.springframework.hateoas.mvc.ControllerLinkBuilder.linkTo;
import static org.springframework.hateoas.mvc.ControllerLinkBuilder.methodOn;

@RestController
@RequestMapping(path = "/trust-anchors", produces = Api.API_MIME_TYPE)
@Slf4j
public class TrustAnchorController {

    private final TrustAnchorRepository trustAnchorRepository;
    private final TrustAnchorService trustAnchorService;

    @Autowired
    public TrustAnchorController(TrustAnchorRepository trustAnchorRepository, TrustAnchorService trustAnchorService) {
        this.trustAnchorRepository = trustAnchorRepository;
        this.trustAnchorService = trustAnchorService;
    }

    @RequestMapping(method = RequestMethod.GET)
    public ResponseEntity<ApiResponse<List<TrustAnchorResource>>> list() {
        return ResponseEntity.ok(ApiResponse.data(
            new Links(linkTo(methodOn(TrustAnchorController.class).list()).withSelfRel()),
            trustAnchorRepository.findAll()
                .stream()
                .map(ta -> TrustAnchorResource.of(ta, linkTo(methodOn(TrustAnchorController.class).get(ta.getId())).withSelfRel()))
                .collect(Collectors.toList())
        ));
    }

    @RequestMapping(method = RequestMethod.POST, consumes = Api.API_MIME_TYPE)
    public ResponseEntity<ApiResponse<TrustAnchorResource>> add(@RequestBody @Valid ApiCommand<AddTrustAnchor> command) {
        long id = trustAnchorService.execute(command.getData());
        TrustAnchor trustAnchor = trustAnchorRepository.get(id);
        Link selfRel = linkTo(methodOn(TrustAnchorController.class).get(id)).withSelfRel();
        return ResponseEntity.created(URI.create(selfRel.getHref())).body(ApiResponse.data(
            TrustAnchorResource.of(trustAnchor, selfRel)
        ));
    }

    @RequestMapping(path = "/{id}", method = RequestMethod.GET)
    public ResponseEntity<ApiResponse<TrustAnchorResource>> get(@PathVariable long id) {
        TrustAnchor trustAnchor = trustAnchorRepository.get(id);
        return ResponseEntity.ok(ApiResponse.data(
            TrustAnchorResource.of(
                trustAnchor,
                linkTo(methodOn(TrustAnchorController.class).get(id)).withSelfRel()
            )
        ));
    }
}
