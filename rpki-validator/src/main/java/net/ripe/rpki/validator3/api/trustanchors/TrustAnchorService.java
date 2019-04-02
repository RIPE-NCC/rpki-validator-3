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
package net.ripe.rpki.validator3.api.trustanchors;

import com.google.common.io.PatternFilenameFilter;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.validator3.background.ValidationScheduler;
import net.ripe.rpki.validator3.domain.RpkiRepositories;
import net.ripe.rpki.validator3.domain.RpkiRepository;
import net.ripe.rpki.validator3.domain.Settings;
import net.ripe.rpki.validator3.domain.TrustAnchor;
import net.ripe.rpki.validator3.domain.TrustAnchors;
import net.ripe.rpki.validator3.domain.ValidatedRpkiObjects;
import net.ripe.rpki.validator3.domain.ValidationRuns;
import net.ripe.rpki.validator3.util.TrustAnchorLocator;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.validation.annotation.Validated;

import javax.annotation.PostConstruct;
import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import javax.validation.Valid;
import java.io.File;
import java.net.URI;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
@Transactional
@Validated
@Slf4j
public class TrustAnchorService {

    @Autowired
    private TrustAnchors trustAnchors;

    @Autowired
    private RpkiRepositories rpkiRepositories;

    @Autowired
    private ValidatedRpkiObjects validatedRpkiObjects;

    @Autowired
    private ValidationRuns validationRunRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private Settings settings;

    @Autowired
    private ValidationScheduler validationScheduler;

    @Value("${rpki.validator.preconfigured.trust.anchors.directory}")
    private File preconfiguredTrustAnchorDirectory;

    @Autowired
    private EntityManager entityManager;

    public long execute(@Valid AddTrustAnchor command) {
        TrustAnchor trustAnchor = new TrustAnchor(false);
        trustAnchor.setName(command.getName());
        trustAnchor.setLocations(command.getLocations());
        trustAnchor.setSubjectPublicKeyInfo(command.getSubjectPublicKeyInfo());
        trustAnchor.setRsyncPrefetchUri(command.getRsyncPrefetchUri());

        if(trustAnchor.getRsyncPrefetchUri()!= null) {
            log.info("Register and schedule prefetch trust anchor '{}'", trustAnchor);
            RpkiRepository prefetchRepo = rpkiRepositories.register(trustAnchor, trustAnchor.getRsyncPrefetchUri(), RpkiRepository.Type.RSYNC_PREFETCH);
            validationScheduler.schedulePrefetchRsync(prefetchRepo);
        }

        long trustAnchorId = addAndScheduleTA(trustAnchor);

        entityManager.flush();
        entityManager.clear();

        return trustAnchorId;
    }


    @PostConstruct
    public void managePreconfiguredAndExistingTrustAnchors() {
        log.info("Automatically adding preconfigured trust anchors");

        if (settings.isPreconfiguredTalsLoaded()) {
            log.info("Preconfigured trust anchors are already loaded, skipping");
            scheduleTasValidation();
            return;
        }

        settings.markPreconfiguredTalsLoaded();

        final File[] tals = preconfiguredTrustAnchorDirectory.listFiles(new PatternFilenameFilter(Pattern.compile("^.*\\.tal$")));
        if (ArrayUtils.isEmpty(tals)) {
            log.warn("No preconfigured trust anchors found at {}, skipping", preconfiguredTrustAnchorDirectory);
            return;
        }

        for (File tal : tals) {
            final TrustAnchorLocator locator = TrustAnchorLocator.fromFile(tal);
            new TransactionTemplate(transactionManager).execute((status) -> {
                Optional<TrustAnchor> ta = trustAnchors.findBySubjectPublicKeyInfo(locator.getPublicKeyInfo());
                if (ta.isPresent()) {
                    log.info("Preconfigured trust anchor '{}' already installed, skipping", locator.getCaName());
                } else {
                    TrustAnchor trustAnchor = new TrustAnchor(true);
                    trustAnchor.setName(locator.getCaName());
                    trustAnchor.setLocations(locator.getCertificateLocations().stream().map(URI::toASCIIString).collect(Collectors.toList()));
                    trustAnchor.setSubjectPublicKeyInfo(locator.getPublicKeyInfo());
                    trustAnchor.setRsyncPrefetchUri(
                            locator.getPrefetchUris().stream()
                                    .filter(uri -> "rsync".equalsIgnoreCase(uri.getScheme()))
                                    .map(URI::toASCIIString)
                                    .findFirst().orElse(null)
                    );
                    if(trustAnchor.getRsyncPrefetchUri()!= null) {
                        log.info("Register only prefetch trust anchor '{}'", trustAnchor);
                        rpkiRepositories.register(trustAnchor, trustAnchor.getRsyncPrefetchUri(), RpkiRepository.Type.RSYNC_PREFETCH);
                    }
                    addAndScheduleTA(trustAnchor);
                }
                return null;
            });
        }

        scheduleTasValidation();
    }

    public void remove(long trustAnchorId) {
        TrustAnchor trustAnchor = trustAnchors.get(trustAnchorId);
        validationRunRepository.removeAllForTrustAnchor(trustAnchor);
        rpkiRepositories.removeAllForTrustAnchor(trustAnchor);
        validationScheduler.removeTrustAnchor(trustAnchor);
        trustAnchors.remove(trustAnchor);
        validatedRpkiObjects.remove(trustAnchor);
    }

    private long addAndScheduleTA(TrustAnchor trustAnchor) {
        trustAnchors.add(trustAnchor);
        validationScheduler.addTrustAnchor(trustAnchor);

        log.info("Add and schedule trust anchor '{}'", trustAnchor);
        return trustAnchor.getId();
    }


    private void scheduleTasValidation() {
        log.info("Schedule TA validation that were in the database already");
        new TransactionTemplate(transactionManager).execute((status) -> {
            trustAnchors.findAll().forEach(ta -> {
                if (!validationScheduler.scheduledTrustAnchor(ta)) {
                    log.info("Adding " + ta.getName() + " to the validation scheduler");
                    validationScheduler.addTrustAnchor(ta);
                }
            });
            return null;
        });
    }
}
