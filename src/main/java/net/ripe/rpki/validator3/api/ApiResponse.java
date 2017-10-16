package net.ripe.rpki.validator3.api;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Builder;
import lombok.Value;
import org.springframework.hateoas.Links;

import java.util.Arrays;
import java.util.List;

@Value(staticConstructor = "of")
@ApiModel(value = "Response")
@Builder
public class ApiResponse<T> {

    @ApiModelProperty(required = false, position = 1)
    Links links;

    @ApiModelProperty(required = false, position = 2)
    T data;

    @ApiModelProperty(required = false, position = 3)
    List<Object> includes;

    @ApiModelProperty(required = false, position = 4)
    List<ApiError> errors;

    public static <T> ApiResponse<T> data(T data) {
        return ApiResponse.<T>builder().data(data).build();
    }

    public static <T> ApiResponse<T> data(Links links, T data) {
        return ApiResponse.<T>builder().links(links).data(data).build();
    }

    public static <T> ApiResponse<T> error(ApiError... errors) {
        return error(Arrays.asList(errors));
    }

    public static <T> ApiResponse<T> error(List<ApiError> errors) {
        return ApiResponse.<T>builder().errors(errors).build();
    }
}
