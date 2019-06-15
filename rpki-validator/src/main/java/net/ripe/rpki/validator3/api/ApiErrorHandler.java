/**
 * The BSD License
 *
 * Copyright (c) 2010-2018 RIPE NCC
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *   - Redistributions of source code must retain the above copyright notice,
 *     this list of conditions and the following disclaimer.
 *   - Redistributions in binary form must reproduce the above copyright notice,
 *     this list of conditions and the following disclaimer in the documentation
 *     and/or other materials provided with the distribution.
 *   - Neither the name of the RIPE NCC nor the names of its contributors may be
 *     used to endorse or promote products derived from this software without
 *     specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package net.ripe.rpki.validator3.api;

import org.springframework.context.MessageSource;

import javax.inject.Inject;

//TODO: Read https://docs.micronaut.io/latest/guide/index.html#aop
// And convert this to micronaut.
//@ControllerAdvice(annotations = RestController.class)
public class ApiErrorHandler {//extends ResponseEntityExceptionHandler {

    @Inject
    private MessageSource messages;
//
//    @ExceptionHandler(value = DataRetrievalFailureException.class)
//    protected ResponseEntity<ApiResponse<?>> handleDataRetrievalFailureException(DataRetrievalFailureException ex, WebRequest request) {
//        HttpHeaders headers = new HttpHeaders();
//        headers.setContentType(MediaType.valueOf(Api.API_MIME_TYPE));
//        return new ResponseEntity<>(ApiResponse.error(ApiError.of(HttpStatus.NOT_FOUND)), headers, HttpStatus.NOT_FOUND);
//    }
//
//    @Override
//    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatus status, WebRequest request) {
//        Locale locale = request.getLocale();
//        List<ApiError> errors = ex.getBindingResult().getFieldErrors().stream().map((fieldError) ->
//            ApiError.builder()
//                .status(String.valueOf(HttpStatus.BAD_REQUEST.value()))
//                .code(fieldError.getCode())
//                .title(messages.getMessage("title." + fieldError.getCode(), null, HttpStatus.BAD_REQUEST.getReasonPhrase(), locale))
//                .detail(messages.getMessage(fieldError, locale))
//                .source(
//                    ApiErrorSource.of(Optional.of("/" + fieldError.getField().replaceAll("[.\\[]", "/").replace("]", "")), Optional.empty())
//                )
//                .build()
//        ).collect(Collectors.toList());
//        return ResponseEntity.badRequest()
//            .contentType(MediaType.valueOf(Api.API_MIME_TYPE))
//            .body(ApiResponse.error(errors));
//    }
//
//    @ExceptionHandler(value = MethodArgumentTypeMismatchException.class)
//    protected ResponseEntity<Object> handleMethodArgumentNotValidException(MethodArgumentTypeMismatchException ex, Locale locale) {
//        ApiError error = ApiError.builder()
//            .status(String.valueOf(HttpStatus.BAD_REQUEST.value()))
//            .code(ex.getErrorCode())
//            .title(messages.getMessage(
//                "title." + ex.getErrorCode(),
//                null,
//                HttpStatus.BAD_REQUEST.getReasonPhrase(),
//                locale
//            ))
//            .detail(messages.getMessage(
//                "methodArgument." + ex.getErrorCode(),
//                new Object[]{ex.getValue(), messages.getMessage("type." + ex.getRequiredType(), null, locale)},
//                locale
//            ))
//            .source(
//                ApiErrorSource.of(Optional.empty(), Optional.of(ex.getName()))
//            )
//            .build();
//        return ResponseEntity.badRequest()
//            .contentType(MediaType.valueOf(Api.API_MIME_TYPE))
//            .body(ApiResponse.error(error));
//    }
}
