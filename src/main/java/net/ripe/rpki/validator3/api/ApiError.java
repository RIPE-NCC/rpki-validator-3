package net.ripe.rpki.validator3.api;

import lombok.Value;

import java.util.Optional;

@Value(staticConstructor = "of")
public class ApiError {
    Optional<String> detail;
}
