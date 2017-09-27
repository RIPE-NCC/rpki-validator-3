package net.ripe.rpki.validator3.api;

import lombok.Value;
import org.springframework.hateoas.Links;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Value(staticConstructor = "of")
public class ApiResponse<T> {

    Optional<Links> links;
    Optional<T> data;
    List<ApiError> errors;

    public static <T> ApiResponse<T> data(T data) {
        return ApiResponse.of(Optional.empty(), Optional.of(data), Collections.emptyList());
    }

    public static <T> ApiResponse<T> data(Links links, T data) {
        return ApiResponse.of(Optional.of(links), Optional.of(data), Collections.emptyList());
    }

    public static <T> ApiResponse<T> error(ApiError... errors) {
        return ApiResponse.of(Optional.empty(), Optional.empty(), Arrays.asList(errors));
    }
}
