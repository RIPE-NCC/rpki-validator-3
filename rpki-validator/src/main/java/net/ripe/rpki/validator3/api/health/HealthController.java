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
package net.ripe.rpki.validator3.api.health;

import com.google.common.collect.Maps;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.validator3.api.Api;
import net.ripe.rpki.validator3.api.ApiResponse;
import net.ripe.rpki.validator3.api.bgp.BgpPreviewService;
import net.ripe.rpki.validator3.api.bgp.BgpRisDump;
import net.ripe.rpki.validator3.api.trustanchors.TaStatus;
import net.ripe.rpki.validator3.api.util.BuildInformation;
import net.ripe.rpki.validator3.api.util.Dates;
import net.ripe.rpki.validator3.background.BackgroundJobs;
import net.ripe.rpki.validator3.storage.Storage;
import net.ripe.rpki.validator3.storage.stores.TrustAnchors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping(path = "/api/healthcheck", produces = {Api.API_MIME_TYPE, "application/json"})
@Slf4j
public class HealthController {

    @Autowired
    private TrustAnchors trustAnchors;

    @Autowired
    private BgpPreviewService bgpPreviewService;

    @Autowired
    private BackgroundJobs backgroundJobs;

    @Autowired
    private BuildInformation buildInformation;

    @Autowired
    private Storage storage;

    @GetMapping
    public ResponseEntity<ApiResponse<Health>> health() {

        final Map<String, Boolean> trustAnchorReady = storage.readTx(tx -> trustAnchors.getStatuses(tx)).stream().
                collect(Collectors.toMap(
                        TaStatus::getTaName,
                        TaStatus::isCompletedValidation)
                );

        final Map<String, Boolean> bgpDumpReady = bgpPreviewService.getBgpDumps().stream().
                collect(Collectors.toMap(
                        BgpRisDump::getUrl,
                        dmp -> dmp.getLastModified() != null
                ));

        final Map<String, String> databaseStatus = databaseStatus();

        return ResponseEntity.ok(ApiResponse.<Health>builder()
                .data(Health.of(trustAnchorReady, bgpDumpReady, databaseStatus, buildInformation))
                .build());
    }

    @GetMapping(path = "/backgrounds")
    public ResponseEntity<ApiResponse<Map<String, BackgroundJobs.Execution>>> check() {
        return ResponseEntity.ok(ApiResponse.<Map<String, BackgroundJobs.Execution>>builder()
                .data(backgroundJobs.getStat())
                .build());
    }

    @GetMapping(path = "/all-ta-completed")
    public ResponseEntity<ApiResponse<String>> statuses() {
        List<TaStatus> statuses = storage.readTx(tx -> trustAnchors.getStatuses(tx));

        Boolean allComplete = statuses.stream().filter(TaStatus::isCompletedValidation).count() >= 5;

        Instant twoHoursAgo = Instant.now().minusSeconds(7200L);
        Boolean completedRecently = statuses.stream()
                .map(s -> Dates.parseUTC(s.getLastUpdated()))
                .sorted()
                .findFirst()
                .map(i -> i.isAfter(twoHoursAgo)).orElse(false);

        if (allComplete && completedRecently)
            return ResponseEntity.ok(ApiResponse.<String>builder()
                    .data("OK")
                    .build());
        else
            return ResponseEntity.noContent().build();
    }


    private Map<String, String> databaseStatus() {
        try {
            return storage.getDbStats();
        } catch (Exception e) {
            return Collections.singletonMap("error", e.getMessage());
        }
    }
}
