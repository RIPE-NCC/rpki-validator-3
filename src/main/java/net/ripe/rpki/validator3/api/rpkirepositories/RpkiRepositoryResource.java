package net.ripe.rpki.validator3.api.rpkirepositories;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import net.ripe.rpki.validator3.api.Api;
import net.ripe.rpki.validator3.domain.RpkiRepository;
import net.ripe.rpki.validator3.domain.constraints.ValidLocationURI;
import org.springframework.hateoas.Links;

import javax.validation.constraints.NotNull;
import java.math.BigInteger;

import static org.springframework.hateoas.mvc.ControllerLinkBuilder.linkTo;
import static org.springframework.hateoas.mvc.ControllerLinkBuilder.methodOn;

@Data(staticConstructor = "of")
public class RpkiRepositoryResource {
    @ApiModelProperty(allowableValues = RpkiRepository.TYPE, required = true, position = 1)
    final String type;

    @ApiModelProperty(required = true, allowableValues = "range[" + Api.MINIMUM_VALID_ID + ",infinity]", example = "1", position = 2)
    final long id;

    @NotNull
    @ValidLocationURI
    final String locationURI;

    @NotNull
    final RpkiRepository.Status status;

    final String rrdpSessionId;

    final BigInteger rrdpSerial;

    final Links links;

    public static RpkiRepositoryResource of(RpkiRepository rpkiRepository) {
        return of(
            RpkiRepository.TYPE,
            rpkiRepository.getId(),
            rpkiRepository.getRrdpNotifyUri(),
            rpkiRepository.getStatus(),
            rpkiRepository.getRrdpSessionId(),
            rpkiRepository.getRrdpSerial(),
            new Links(
                linkTo(methodOn(RpkiRepositoriesController.class).get(rpkiRepository.getId())).withSelfRel()
            )
        );
    }
}
