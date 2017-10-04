package net.ripe.rpki.validator3.api;

import io.swagger.annotations.ApiModelProperty;
import lombok.Value;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

@Value(staticConstructor = "of")
public class ApiCommand<T> {
    @NotNull
    @Valid
    @ApiModelProperty(required = true)
    T data;
}
