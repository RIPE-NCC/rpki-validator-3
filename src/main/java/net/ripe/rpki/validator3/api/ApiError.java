package net.ripe.rpki.validator3.api;

import io.swagger.annotations.ApiModel;
import lombok.Builder;
import lombok.Value;
import org.springframework.http.HttpStatus;

@Value(staticConstructor = "of")
@Builder
@ApiModel(value = "Error")
public class ApiError {
    String status;
    String code;
    String title;
    String detail;
    ApiErrorSource source;

    public static ApiError of(HttpStatus status) {
        return ApiError.builder().status(String.valueOf(status.value())).title(status.getReasonPhrase()).build();
    }

    public static ApiError of(HttpStatus status, String detail) {
        return ApiError.builder().status(String.valueOf(status.value())).title(status.getReasonPhrase()).detail(detail).build();
    }
}
