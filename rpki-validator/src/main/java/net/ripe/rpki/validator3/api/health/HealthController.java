package net.ripe.rpki.validator3.api.health;

import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.validator3.api.Api;
import net.ripe.rpki.validator3.api.ApiResponse;
import net.ripe.rpki.validator3.api.bgp.BgpPreviewService;
import net.ripe.rpki.validator3.api.bgp.BgpRisDump;
import net.ripe.rpki.validator3.api.trustanchors.TaStatus;
import net.ripe.rpki.validator3.domain.TrustAnchors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.persistence.EntityManager;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping(path = "/api/healthcheck", produces = {Api.API_MIME_TYPE, "application/json"})
@Slf4j
public class HealthController {

    @Autowired
    private TrustAnchors trustAnchorRepository;

    @Autowired
    private BgpPreviewService bgpPreviewService;

    @Autowired
    private EntityManager entityManager;

    @GetMapping(path = "/")
    public ResponseEntity<ApiResponse<Health>> health() {

        final Map<String, Boolean> trustAnchorReady = trustAnchorRepository.getStatuses().stream().
                collect(Collectors.toMap(
                        TaStatus::getTaName,
                        TaStatus::isCompletedValidation)
                );

        final Map<String, Boolean> bgpDumpReady = bgpPreviewService.getBgpDumps().stream().
                collect(Collectors.toMap(
                        BgpRisDump::getUrl,
                        // the dump was updated not more than 20 minutes ago
                        dmp -> dmp.getLastModified() != null && dmp.getLastModified().plusMinutes(20).isAfterNow()
                ));

        final String databaseStatus = databaseStatus();

        return ResponseEntity.ok(ApiResponse.<Health>builder()
                .data(Health.of(trustAnchorReady, bgpDumpReady, databaseStatus))
                .build());
    }


    private String databaseStatus() {
        try {
            entityManager.createNativeQuery("SELECT 1").getSingleResult();
            return "OK";
        } catch (Exception e) {
            return e.getMessage();
        }
    }
}
