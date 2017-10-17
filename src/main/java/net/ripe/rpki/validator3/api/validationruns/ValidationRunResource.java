package net.ripe.rpki.validator3.api.validationruns;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Builder;
import lombok.Data;
import net.ripe.rpki.validator3.api.trustanchors.TrustAnchorController;
import net.ripe.rpki.validator3.domain.TrustAnchor;
import net.ripe.rpki.validator3.domain.ValidationRun;
import org.springframework.hateoas.Links;

import java.util.List;
import java.util.stream.Collectors;

import static org.springframework.hateoas.mvc.ControllerLinkBuilder.linkTo;
import static org.springframework.hateoas.mvc.ControllerLinkBuilder.methodOn;

@Data
@Builder
@ApiModel(value = "ValidationRun")
public class ValidationRunResource {
    @ApiModelProperty(allowableValues = "trust-anchor-validation-run,rpki-repository-validation-run", required = true, position = 1)
    String type;

    String status;

    List<ValidationCheckResource> validationChecks;

    Links links;

    public static ValidationRunResource of(ValidationRun validationRun) {
        return ValidationRunResource.builder()
            .type(validationRun.getType())
            .status(validationRun.getStatus().name())
            .validationChecks(
                validationRun.getValidationChecks()
                    .stream()
                    .map(ValidationCheckResource::of)
                    .collect(Collectors.toList())
            )
            .links(new Links(
                linkTo(methodOn(ValidationRunController.class).get(validationRun.getId())).withSelfRel(),
                linkTo(methodOn(TrustAnchorController.class).get(validationRun.getTrustAnchor().getId())).withRel(TrustAnchor.TYPE)
            ))
            .build();
    }
}
