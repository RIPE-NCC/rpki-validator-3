package net.ripe.rpki.validator3.api.validationruns;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Builder;
import lombok.Data;
import net.ripe.rpki.validator3.api.rpkirepositories.RpkiRepositoriesController;
import net.ripe.rpki.validator3.api.trustanchors.TrustAnchorController;
import net.ripe.rpki.validator3.domain.*;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.Links;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.springframework.hateoas.mvc.ControllerLinkBuilder.linkTo;
import static org.springframework.hateoas.mvc.ControllerLinkBuilder.methodOn;

@Data
@Builder
@ApiModel(value = "ValidationRun")
public class ValidationRunResource {
    @ApiModelProperty(allowableValues = TrustAnchorValidationRun.TYPE + "," + RpkiRepositoryValidationRun.TYPE, required = true, position = 1)
    String type;

    String status;

    List<ValidationCheckResource> validationChecks;

    Links links;

    public static ValidationRunResource of(ValidationRun validationRun) {
        List<Link> links = new ArrayList<>();
        links.add(linkTo(methodOn(ValidationRunController.class).get(validationRun.getId())).withSelfRel());
        links.add(linkTo(methodOn(TrustAnchorController.class).get(validationRun.getTrustAnchor().getId())).withRel(TrustAnchor.TYPE));

        switch (validationRun.getType()) {
            case RpkiRepositoryValidationRun.TYPE:
                RpkiRepositoryValidationRun run = (RpkiRepositoryValidationRun) validationRun;
                links.add(linkTo(methodOn(RpkiRepositoriesController.class).get(run.getRpkiRepository().getId())).withRel(RpkiRepository.TYPE));
                break;
        }

        return ValidationRunResource.builder()
            .type(validationRun.getType())
            .status(validationRun.getStatus().name())
            .validationChecks(
                validationRun.getValidationChecks()
                    .stream()
                    .map(ValidationCheckResource::of)
                    .collect(Collectors.toList())
            )
            .links(new Links(links))
            .build();
    }
}
