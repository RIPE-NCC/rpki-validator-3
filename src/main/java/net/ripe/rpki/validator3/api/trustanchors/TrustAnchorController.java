package net.ripe.rpki.validator3.api.trustanchors;

import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.validator3.api.Api;
import net.ripe.rpki.validator3.api.ApiCommand;
import net.ripe.rpki.validator3.api.ApiError;
import net.ripe.rpki.validator3.api.ApiResponse;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping(path = "/trust-anchors", consumes = Api.API_MIME_TYPE, produces = Api.API_MIME_TYPE)
@Slf4j
public class TrustAnchorController {
    @RequestMapping(method = RequestMethod.GET)
    public ApiResponse<TrustAnchorListResult> index() {
        return ApiResponse.of(TrustAnchorListResult.of(Arrays.asList(
            TrustAnchorInfo.of(
                UUID.randomUUID(),
                "RIPE NCC",
                URI.create("rsync://rpki.ripe.net/ta/ripe-ncc-ta.cer"),
                "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA0URYSGqUz2myBsOzeW1jQ6NsxNvlLMyhWknvnl8NiBCs/T/S2XuNKQNZ+wBZxIgPPV2pFBFeQAvoH/WK83HwA26V2siwm/MY2nKZ+Olw+wlpzlZ1p3Ipj2eNcKrmit8BwBC8xImzuCGaV0jkRB0GZ0hoH6Ml03umLprRsn6v0xOP0+l6Qc1ZHMFVFb385IQ7FQQTcVIxrdeMsoyJq9eMkE6DoclHhF/NlSllXubASQ9KUWqJ0+Ot3QCXr4LXECMfkpkVR2TZT+v5v658bHVs6ZxRD1b6Uk1uQKAyHUbn/tXvP8lrjAibGzVsXDT2L0x4Edx+QdixPgOji3gBMyL2VwIDAQAB",
                Optional.of(URI.create("rsync://rpki.ripe.net/repository/")),
                Optional.of(new byte[]{(byte) 0xde, (byte) 0xad, (byte) 0xbe, (byte) 0xef})
            )
        )));
    }

    @RequestMapping(method = RequestMethod.POST)
    public ApiResponse<AddTrustAnchorResult> add(@RequestBody ApiCommand<AddTrustAnchor> command) {
        log.info("{}", command);
        return ApiResponse.<AddTrustAnchorResult>of(ApiError.of(Optional.of("not implemented")));
    }

}
