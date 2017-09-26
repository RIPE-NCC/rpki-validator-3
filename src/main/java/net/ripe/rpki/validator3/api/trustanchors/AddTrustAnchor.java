package net.ripe.rpki.validator3.api.trustanchors;

import lombok.Data;
import net.ripe.rpki.validator3.domain.constraints.ValidLocationURI;
import net.ripe.rpki.validator3.domain.constraints.ValidPublicKeyInfo;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.List;

@Data(staticConstructor = "of")
class AddTrustAnchor {
    String type = "trust-anchor";

    @NotNull
    String name;

    @NotNull
    @Size(min = 1, max = 1)
    List<@NotNull @ValidLocationURI String> locations;

    @NotNull
    @ValidPublicKeyInfo
    String subjectPublicKeyInfo;
}
