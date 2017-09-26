package net.ripe.rpki.validator3.api.trustanchors;

import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.validator3.api.Api;
import net.ripe.rpki.validator3.api.ApiCommand;
import net.ripe.rpki.validator3.api.ApiResponse;
import net.ripe.rpki.validator3.domain.TrustAnchor;
import net.ripe.rpki.validator3.domain.TrustAnchorRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.stream.Collectors;

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
    public ApiResponse<TrustAnchorListResult> index() {
        return ApiResponse.of(TrustAnchorListResult.of(
            trustAnchorRepository.findAll()
                .stream()
                .map(TrustAnchorInfo::of)
                .collect(Collectors.toList())
        ));
    }

    @RequestMapping(method = RequestMethod.POST, consumes = Api.API_MIME_TYPE)
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TrustAnchorInfo> add(@RequestBody @Valid ApiCommand<AddTrustAnchor> command) {
        long id = trustAnchorService.execute(command.getData());
        TrustAnchor trustAnchor = trustAnchorRepository.get(id);
        return ApiResponse.of(TrustAnchorInfo.of(trustAnchor));
    }
}
