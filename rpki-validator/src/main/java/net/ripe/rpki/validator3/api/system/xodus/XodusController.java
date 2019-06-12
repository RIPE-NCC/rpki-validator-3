package net.ripe.rpki.validator3.api.system.xodus;

import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.validator3.api.Api;
import net.ripe.rpki.validator3.domain.cleanup.ValidationRunCleanupService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/xodus", produces = {Api.API_MIME_TYPE, "application/json"})
@Slf4j
public class XodusController {

    @Autowired
    private ValidationRunCleanupService validationRunCleanupService;

    @GetMapping(path = "/clean-vr")
    public void clean() {
        validationRunCleanupService.cleanupValidationRuns();
    }

}
