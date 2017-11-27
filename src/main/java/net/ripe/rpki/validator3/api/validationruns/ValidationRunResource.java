package net.ripe.rpki.validator3.api.validationruns;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Builder;
import lombok.Data;
import net.ripe.rpki.validator3.api.trustanchors.TrustAnchorController;
import net.ripe.rpki.validator3.domain.CertificateTreeValidationRun;
import net.ripe.rpki.validator3.domain.RpkiRepositoryValidationRun;
import net.ripe.rpki.validator3.domain.RrdpRepositoryValidationRun;
import net.ripe.rpki.validator3.domain.RsyncRepositoryValidationRun;
import net.ripe.rpki.validator3.domain.TrustAnchor;
import net.ripe.rpki.validator3.domain.TrustAnchorValidationRun;
import net.ripe.rpki.validator3.domain.ValidationRun;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.Links;

import java.time.Instant;
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

    Instant startedAt;

    Instant completedAt;

    String status;

    List<ValidationCheckResource> validationChecks;

    Integer validatedObjectCount;

    Integer addedObjectCount;

    Links links;

    public static ValidationRunResource of(ValidationRun validationRun) {
        List<Link> links = new ArrayList<>();
        links.add(linkTo(methodOn(ValidationRunController.class).get(validationRun.getId())).withSelfRel());

        ValidationRunResourceBuilder builder = ValidationRunResource.builder()
            .type(validationRun.getType())
            .startedAt(validationRun.getCreatedAt())
            .completedAt(validationRun.getCompletedAt())
            .status(validationRun.getStatus().name())
            .validationChecks(
                validationRun.getValidationChecks()
                    .stream()
                    .map(ValidationCheckResource::of)
                    .collect(Collectors.toList())
            );

        validationRun.visit(new ValidationRun.Visitor() {
            @Override
            public void accept(CertificateTreeValidationRun validationRun) {
                builder.validatedObjectCount(validationRun.getValidatedObjects().size());
                links.add(linkTo(methodOn(TrustAnchorController.class).get(validationRun.getTrustAnchor().getId())).withRel(TrustAnchor.TYPE));
            }

            @Override
            public void accept(RrdpRepositoryValidationRun validationRun) {
                builder.addedObjectCount(validationRun.getAddedObjectCount());
            }

            @Override
            public void accept(RsyncRepositoryValidationRun validationRun) {
                builder.addedObjectCount(validationRun.getAddedObjectCount());
            }

            @Override
            public void accept(TrustAnchorValidationRun validationRun) {
                links.add(linkTo(methodOn(TrustAnchorController.class).get(validationRun.getTrustAnchor().getId())).withRel(TrustAnchor.TYPE));
            }
        });

        return builder.links(new Links(links)).build();
    }
}
