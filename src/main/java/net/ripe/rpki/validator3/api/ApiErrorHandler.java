package net.ripe.rpki.validator3.api;

import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@ControllerAdvice
public class ApiErrorHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(value = DataRetrievalFailureException.class)
    protected ResponseEntity<ApiResponse<?>> handleConflict(DataRetrievalFailureException ex, WebRequest request) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.valueOf(Api.API_MIME_TYPE));
        return new ResponseEntity<>(ApiResponse.error(ApiError.of(HttpStatus.NOT_FOUND)), headers, HttpStatus.NOT_FOUND);
    }
}
