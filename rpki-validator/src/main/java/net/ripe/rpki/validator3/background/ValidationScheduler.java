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
package net.ripe.rpki.validator3.background;

import com.google.common.base.Preconditions;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.validator3.api.ValidatorApi;
import net.ripe.rpki.validator3.domain.validation.CertificateTreeValidationService;
import net.ripe.rpki.validator3.domain.validation.RpkiRepositoryValidationService;
import net.ripe.rpki.validator3.domain.validation.TrustAnchorValidationService;
import net.ripe.rpki.validator3.storage.data.RpkiRepository;
import net.ripe.rpki.validator3.storage.data.TrustAnchor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class ValidationScheduler {

    private final BackgroundJobs backgroundJobs;

    @Getter
    private final Duration rsyncRepositoryDownloadInterval;
    @Getter
    private final Duration rrpdRepositoryDownloadInterval;

    private final Throttled<String> throttledTreeValidation;

    private boolean enabled = true;

    private final CertificateTreeValidationService validationService;

    private final TrustAnchorValidationService trustAnchorValidationService;

    private final RpkiRepositoryValidationService rpkiRepositoryValidationService;

    @Autowired
    public ValidationScheduler(@Value("${rpki.validator.rsync.repository.download.interval:PT10M}") String rsyncRepositoryDownloadInterval,
                               @Value("${rpki.validator.rrdp.repository.download.interval:PT2M}") String rrpdRepositoryDownloadInterval,
                               @Lazy CertificateTreeValidationService validationService,
                               Environment environment,
                               @Lazy BackgroundJobs backgroundJobs,
                               @Lazy TrustAnchorValidationService trustAnchorValidationService,
                               @Lazy RpkiRepositoryValidationService rpkiRepositoryValidationService) {
        this.rsyncRepositoryDownloadInterval = Duration.parse(rsyncRepositoryDownloadInterval);
        this.rrpdRepositoryDownloadInterval = Duration.parse(rrpdRepositoryDownloadInterval);
        this.validationService = validationService;
        this.backgroundJobs = backgroundJobs;
        this.trustAnchorValidationService = trustAnchorValidationService;
        this.rpkiRepositoryValidationService = rpkiRepositoryValidationService;

        this.throttledTreeValidation = new Throttled<>(30_000);

        // Disable scheduling during tests
        if (environment.acceptsProfiles(Profiles.of("test"))) {
            this.disable();
        }
    }

    public void addTrustAnchor(TrustAnchor trustAnchor) {
        if (!enabled) {
            return;
        }
        Preconditions.checkArgument(
            trustAnchor.key().asLong() >= ValidatorApi.MINIMUM_VALID_ID,
            "trustAnchor id %s is not valid",
            trustAnchor.key()
        );

        backgroundJobs.schedule(
            Job.of(BackgroundJobs.getTaValidationJobKey(trustAnchor),
                () -> trustAnchorValidationService.validate(trustAnchor.key().asLong())),
            0, (int) rsyncRepositoryDownloadInterval.getSeconds(), TimeUnit.SECONDS);
    }

    public boolean scheduledTrustAnchor(TrustAnchor trustAnchor) {
        if (!enabled) {
            return false;
        }
        return backgroundJobs.jobExists(BackgroundJobs.getTaValidationJobKey(trustAnchor));
    }

    public synchronized void addRrdpRpkiRepository(RpkiRepository rpkiRepository) {
        if (!enabled) {
            return;
        }
        // Minutely scheduling only for RRDP
        Preconditions.checkArgument(rpkiRepository.getType() == RpkiRepository.Type.RRDP);

        Preconditions.checkArgument(
            rpkiRepository.key().asLong() >= ValidatorApi.MINIMUM_VALID_ID,
            "rpkiRepository id %s is not valid",
            rpkiRepository.key()
        );
        if (!backgroundJobs.jobExists(BackgroundJobs.getRrdpRepoValidationJobKey(rpkiRepository))) {
            log.info("Adding repository to the scheduler {}", rpkiRepository);

            backgroundJobs.schedule(
                Job.of(BackgroundJobs.getRrdpRepoValidationJobKey(rpkiRepository),
                    () -> rpkiRepositoryValidationService.validateRrdpRpkiRepository(rpkiRepository.key().asLong())),
                0, (int) rrpdRepositoryDownloadInterval.getSeconds(), TimeUnit.SECONDS);
        }
    }

    public void removeRrdpRpkiRepository(RpkiRepository repository) {
        if (!enabled) {
            return;
        }
        boolean jobDeleted = backgroundJobs.deleteJob(BackgroundJobs.getRrdpRepoValidationJobKey(repository));
        if (!jobDeleted) {
            throw new EmptyResultDataAccessException("validation job for RPKI repository not found", 1);
        }
    }

    public void triggerCertificateTreeValidation(TrustAnchor trustAnchor) {
        if (!enabled) {
            return;
        }
        throttledTreeValidation.trigger(trustAnchor.getName(), () -> {
            log.debug("Re-validating the CA tree for TA {}", trustAnchor.getName());
            validationService.validate(trustAnchor.getId().asLong());
        });
    }

    public void disable() {
        this.enabled = false;
    }
}
