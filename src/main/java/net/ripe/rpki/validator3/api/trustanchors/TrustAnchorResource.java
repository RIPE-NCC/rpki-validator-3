package net.ripe.rpki.validator3.api.trustanchors;

import io.swagger.annotations.ApiModelProperty;
import lombok.Value;
import net.ripe.rpki.validator3.api.Api;
import net.ripe.rpki.validator3.domain.TrustAnchor;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.Links;

import java.util.List;

@Value(staticConstructor = "of")
class TrustAnchorResource {
    @ApiModelProperty(allowableValues = TrustAnchor.TYPE, required = true, position = 1)
    String type;
    @ApiModelProperty(required = true, allowableValues = "range[" + Api.MINIMUM_VALID_ID + ",infinity]", position = 2)
    long id;
    @ApiModelProperty(required = true, position = 3)
    String name;
    @ApiModelProperty(required = true, position = 4)
    List<String> locations;
    @ApiModelProperty(required = true, position = 5)
    String subjectPublicKeyInfo;
    @ApiModelProperty(required = true, position = Integer.MAX_VALUE)
    Links links;

    static TrustAnchorResource of(TrustAnchor trustAnchor, Link selfRel) {
        return of(
            "trust-anchor",
            trustAnchor.getId(),
            trustAnchor.getName(),
            trustAnchor.getLocations(),
            trustAnchor.getSubjectPublicKeyInfo(),
            new Links(selfRel)
        );
    }
}
