package net.ripe.rpki.validator3.api;

import lombok.Value;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Value(staticConstructor = "of")
public class ApiResponse<T> {

    Optional<T> data;
    List<ApiError> errors;

    public static <T> ApiResponse<T> of(T data) {
        return ApiResponse.of(Optional.of(data), Collections.emptyList());
    }

    public static <T> ApiResponse<T> of(ApiError... errors) {
        return ApiResponse.of(Optional.empty(), Arrays.asList(errors));
    }
}
