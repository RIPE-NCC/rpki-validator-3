package net.ripe.rpki.validator3.api.ignorefilters;

import io.swagger.annotations.ApiModelProperty;
import lombok.Builder;
import lombok.Data;

@Data(staticConstructor = "of")
@Builder
public class AddIgnoreFilter {

    @ApiModelProperty(position = 1)
    String asn;

    @ApiModelProperty(position = 2)
    String prefix;

    @ApiModelProperty(position = 3)
    String comment;
}

