package net.ripe.rpki.validator3.api.validationruns;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Builder;
import lombok.Data;
import net.ripe.rpki.validator3.api.trustanchors.TrustAnchorController;
import net.ripe.rpki.validator3.domain.TrustAnchor;
import net.ripe.rpki.validator3.domain.ValidationRun;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.Links;

import java.util.List;
import java.util.stream.Collectors;

import static org.springframework.hateoas.mvc.ControllerLinkBuilder.linkTo;
import static org.springframework.hateoas.mvc.ControllerLinkBuilder.methodOn;

@Data
@Builder
@ApiModel(value = "ValidationRun")
public class ValidationRunResource {
    @ApiModelProperty(allowableValues = ValidationRun.TYPE, required = true, position = 1)
    String type;

    String status;
    String failureMessage;

    List<ValidationCheckResource> validationChecks;

    Links links;

    public static ValidationRunResource of(ValidationRun validationRun, Link selfRel) {
        return ValidationRunResource.builder()
            .type(ValidationRun.TYPE)
            .status(validationRun.getStatus().name())
            .failureMessage(validationRun.getFailureMessage())
            .validationChecks(validationRun.getValidationChecks().stream().map(c -> ValidationCheckResource.of(c.getLocation(), c.getStatus(), c.getKey(), c.getParameters())).collect(Collectors.toList()))
            .links(new Links(
                linkTo(methodOn(ValidationRunController.class).get(validationRun.getId())).withSelfRel(),
                linkTo(methodOn(TrustAnchorController.class).get(validationRun.getTrustAnchor().getId())).withRel(TrustAnchor.TYPE)
            ))
            .build();
    }
}
