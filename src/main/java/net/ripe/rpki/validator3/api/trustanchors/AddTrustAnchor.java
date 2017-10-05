package net.ripe.rpki.validator3.api.trustanchors;

import io.swagger.annotations.ApiModelProperty;
import lombok.Builder;
import lombok.Data;
import net.ripe.rpki.validator3.domain.TrustAnchor;
import net.ripe.rpki.validator3.domain.constraints.ValidLocationURI;
import net.ripe.rpki.validator3.domain.constraints.ValidPublicKeyInfo;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import java.util.List;

@Data(staticConstructor = "of")
@Builder
class AddTrustAnchor {
    @NotNull
    @Pattern(regexp = "^trust-anchor$")
    @ApiModelProperty(allowableValues = TrustAnchor.TYPE, required = true, position = 1)
    String type;

    @NotNull
    @NotEmpty
    @ApiModelProperty(required = true, position = 2)
    String name;

    @NotNull
    @Size(min = 1, max = 1)
    @ApiModelProperty(required = true, position = 3)
    List<@NotNull @ValidLocationURI String> locations;

    @NotNull
    @ValidPublicKeyInfo
    @ApiModelProperty(required = true, position = 4)
    String subjectPublicKeyInfo;
}
