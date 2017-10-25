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

        switch (validationRun.getType()) {
            case CertificateTreeValidationRun.TYPE: {
                CertificateTreeValidationRun run = (CertificateTreeValidationRun) validationRun;
                builder.validatedObjectCount(run.getValidatedObjects().size());
                links.add(linkTo(methodOn(TrustAnchorController.class).get(run.getTrustAnchor().getId())).withRel(TrustAnchor.TYPE));
                break;
            }
            case RpkiRepositoryValidationRun.TYPE: {
                RpkiRepositoryValidationRun run = (RpkiRepositoryValidationRun) validationRun;
                builder.addedObjectCount(run.getAddedObjectCount());
                links.add(linkTo(methodOn(RpkiRepositoriesController.class).get(run.getRpkiRepository().getId())).withRel(RpkiRepository.TYPE));
                break;
            }
            case TrustAnchorValidationRun.TYPE: {
                TrustAnchorValidationRun run = (TrustAnchorValidationRun) validationRun;
                links.add(linkTo(methodOn(TrustAnchorController.class).get(run.getTrustAnchor().getId())).withRel(TrustAnchor.TYPE));
                break;
            }
        }

        return builder.links(new Links(links)).build();
    }
}
