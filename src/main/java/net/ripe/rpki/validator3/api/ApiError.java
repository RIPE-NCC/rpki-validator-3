package net.ripe.rpki.validator3.api;

import io.swagger.annotations.ApiModel;
import lombok.Value;
import org.springframework.http.HttpStatus;

import java.util.Optional;

@Value(staticConstructor = "of")
@ApiModel(value = "Error")
public class ApiError {
    Optional<String> status;
    Optional<String> title;
    Optional<String> detail;

    public static ApiError of(HttpStatus status) {
        return ApiError.of(Optional.of(Integer.toString(status.value())), Optional.of(status.getReasonPhrase()), Optional.empty());
    }

    public static ApiError of(HttpStatus status, String detail) {
        return ApiError.of(Optional.of(Integer.toString(status.value())), Optional.of(status.getReasonPhrase()), Optional.of(detail));
    }
}
