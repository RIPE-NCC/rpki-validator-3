package net.ripe.rpki.validator3.api;

import io.swagger.annotations.ApiModel;
import lombok.Value;
import org.springframework.hateoas.Links;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Value(staticConstructor = "of")
@ApiModel(value = "Response")
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
        return error(Arrays.asList(errors));
    }

    public static <T> ApiResponse<T> error(List<ApiError> errors) {
        return ApiResponse.of(Optional.empty(), Optional.empty(), errors);
    }
}
