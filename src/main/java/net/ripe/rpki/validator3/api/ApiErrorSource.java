package net.ripe.rpki.validator3.api;

import io.swagger.annotations.ApiModel;
import lombok.Value;

import java.util.Optional;

@Value(staticConstructor = "of")
@ApiModel(value = "Source")
public class ApiErrorSource {
    Optional<String> pointer;
    Optional<String> parameter;
}
