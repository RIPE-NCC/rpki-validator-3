package net.ripe.rpki.validator3.api.util;

import lombok.extern.slf4j.Slf4j;
import net.ripe.ipresource.IpRange;
import net.ripe.rpki.validator3.api.Api;
import net.ripe.rpki.validator3.api.ApiError;
import net.ripe.rpki.validator3.api.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/validate", produces = {Api.API_MIME_TYPE, "application/json"})
@Slf4j
public class CommonValidationController {

    @GetMapping(path = "/prefix")
    public ResponseEntity<ApiResponse<String>> validatePrefix(
            @RequestParam(name = "p") String prefix)
    {
        try {
            IpRange.parse(prefix);
            return ResponseEntity.ok(ApiResponse.<String>builder()
                    .data("OK")
                    .build());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(ApiError.of(
                    HttpStatus.BAD_REQUEST, e.getMessage()
            )));
        }
    }

}
