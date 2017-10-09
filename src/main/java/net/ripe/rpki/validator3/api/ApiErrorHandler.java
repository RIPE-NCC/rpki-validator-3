package net.ripe.rpki.validator3.api;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

@ControllerAdvice(annotations = RestController.class)
public class ApiErrorHandler extends ResponseEntityExceptionHandler {

    @Autowired
    private MessageSource messages;

    @ExceptionHandler(value = DataRetrievalFailureException.class)
    protected ResponseEntity<ApiResponse<?>> handleDataRetrievalFailureException(DataRetrievalFailureException ex, WebRequest request) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.valueOf(Api.API_MIME_TYPE));
        return new ResponseEntity<>(ApiResponse.error(ApiError.of(HttpStatus.NOT_FOUND)), headers, HttpStatus.NOT_FOUND);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatus status, WebRequest request) {
        Locale locale = request.getLocale();
        List<ApiError> errors = ex.getBindingResult().getFieldErrors().stream().map((fieldError) ->
            ApiError.builder()
                .status(String.valueOf(HttpStatus.BAD_REQUEST.value()))
                .code(fieldError.getCode())
                .title(messages.getMessage("title." + fieldError.getCode(), null, HttpStatus.BAD_REQUEST.getReasonPhrase(), locale))
                .detail(messages.getMessage(fieldError, locale))
                .source(
                    ApiErrorSource.of(Optional.of("/" + fieldError.getField().replaceAll("[.\\[]", "/").replace("]", "")), Optional.empty())
                )
                .build()
        ).collect(Collectors.toList());
        return ResponseEntity.badRequest()
            .contentType(MediaType.valueOf(Api.API_MIME_TYPE))
            .body(ApiResponse.error(errors));
    }

    @ExceptionHandler(value = MethodArgumentTypeMismatchException.class)
    protected ResponseEntity<Object> handleMethodArgumentNotValidException(MethodArgumentTypeMismatchException ex, Locale locale) {
        ApiError error = ApiError.builder()
            .status(String.valueOf(HttpStatus.BAD_REQUEST.value()))
            .code(ex.getErrorCode())
            .title(messages.getMessage(
                "title." + ex.getErrorCode(),
                null,
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                locale
            ))
            .detail(messages.getMessage(
                "methodArgument." + ex.getErrorCode(),
                new Object[]{ex.getValue(), messages.getMessage("type." + ex.getRequiredType(), null, locale)},
                locale
            ))
            .source(
                ApiErrorSource.of(Optional.empty(), Optional.of(ex.getName()))
            )
            .build();
        return ResponseEntity.badRequest()
            .contentType(MediaType.valueOf(Api.API_MIME_TYPE))
            .body(ApiResponse.error(error));
    }
}
