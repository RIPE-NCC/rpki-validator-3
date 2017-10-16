package net.ripe.rpki.validator3.api.trustanchors;

import io.swagger.annotations.ApiModelProperty;
import lombok.Value;
import net.ripe.rpki.validator3.api.Api;
import net.ripe.rpki.validator3.domain.TrustAnchor;
import org.springframework.hateoas.Links;

import java.util.List;

import static org.springframework.hateoas.mvc.ControllerLinkBuilder.linkTo;
import static org.springframework.hateoas.mvc.ControllerLinkBuilder.methodOn;

@Value(staticConstructor = "of")
class TrustAnchorResource {
    @ApiModelProperty(allowableValues = TrustAnchor.TYPE, required = true, position = 1)
    String type;
    @ApiModelProperty(required = true, allowableValues = "range[" + Api.MINIMUM_VALID_ID + ",infinity]", example = "1", position = 2)
    long id;
    @ApiModelProperty(required = true, example = "RPKI CA", position = 3)
    String name;
    @ApiModelProperty(required = true, position = 4)
    List<String> locations;
    @ApiModelProperty(required = true, position = 5)
    String subjectPublicKeyInfo;
    @ApiModelProperty(position = 6)
    byte[] certificate;
    @ApiModelProperty(required = true, position = 7)
    Links links;

    static TrustAnchorResource of(TrustAnchor trustAnchor) {
        return of(
            "trust-anchor",
            trustAnchor.getId(),
            trustAnchor.getName(),
            trustAnchor.getLocations(),
            trustAnchor.getSubjectPublicKeyInfo(),
            trustAnchor.getCertificate() == null ? null : trustAnchor.getCertificate().getEncoded(),
            new Links(
                linkTo(methodOn(TrustAnchorController.class).get(trustAnchor.getId())).withSelfRel()
            )
        );
    }
}
