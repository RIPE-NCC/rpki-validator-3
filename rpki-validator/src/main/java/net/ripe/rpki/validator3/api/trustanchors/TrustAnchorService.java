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
import net.ripe.rpki.validator3.domain.validation.TrustAnchorState;
import net.ripe.rpki.validator3.domain.validation.ValidatedRpkiObjects;
import net.ripe.rpki.validator3.storage.Tx;
import net.ripe.rpki.validator3.storage.data.Key;
import net.ripe.rpki.validator3.storage.data.Ref;
import net.ripe.rpki.validator3.storage.data.RpkiRepository;
import net.ripe.rpki.validator3.storage.data.TrustAnchor;
import net.ripe.rpki.validator3.storage.Storage;
import net.ripe.rpki.validator3.storage.stores.RpkiRepositories;
import net.ripe.rpki.validator3.storage.stores.TrustAnchors;
import net.ripe.rpki.validator3.util.TrustAnchorLocator;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import javax.annotation.PostConstruct;
import javax.validation.Valid;
import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
@Validated
@Slf4j
public class TrustAnchorService {

    private final TrustAnchors trustAnchors;

    private final RpkiRepositories rpkiRepositories;

    private final ValidatedRpkiObjects validatedRpkiObjects;

    private final ValidationScheduler validationScheduler;

    private final TrustAnchorState trustAnchorState;

    @Value("${rpki.validator.preconfigured.trust.anchors.directory}")
    private File preconfiguredTrustAnchorDirectory;

    private final Storage storage;

    @Autowired
    public TrustAnchorService(TrustAnchors trustAnchors,
                              RpkiRepositories rpkiRepositories,
                              ValidatedRpkiObjects validatedRpkiObjects,
                              ValidationScheduler validationScheduler,
                              TrustAnchorState trustAnchorState, Storage storage) {
        this.trustAnchors = trustAnchors;
        this.rpkiRepositories = rpkiRepositories;
        this.validatedRpkiObjects = validatedRpkiObjects;
        this.validationScheduler = validationScheduler;
        this.trustAnchorState = trustAnchorState;
        this.storage = storage;
    }

    public long execute(@Valid AddTrustAnchor command) {
        TrustAnchor trustAnchor = new TrustAnchor(false);
        trustAnchor.setName(command.getName());
        trustAnchor.setLocations(new ArrayList<>(command.getLocations()));
        trustAnchor.setSubjectPublicKeyInfo(command.getSubjectPublicKeyInfo());
        trustAnchor.setRsyncPrefetchUri(command.getRsyncPrefetchUri());
        return storage.writeTx(tx -> add(tx, trustAnchor));
    }

    long add(Tx.Write tx, TrustAnchor trustAnchor) {
        trustAnchors.add(tx, trustAnchor);

        final Ref<TrustAnchor> trustAnchorRef = trustAnchors.makeRef(tx, trustAnchor.key());
        if (trustAnchor.getRsyncPrefetchUri() != null) {
            rpkiRepositories.register(tx, trustAnchorRef,
                    trustAnchor.getRsyncPrefetchUri(), RpkiRepository.Type.RSYNC_PREFETCH);
        }
        tx.afterCommit(() -> validationScheduler.addTrustAnchor(trustAnchor));
        log.info("Added trust anchor '{}'", trustAnchor);
        return trustAnchor.key().asLong();
    }

    public void remove(long trustAnchorId) {
        storage.writeTx0(tx ->
                trustAnchors.get(tx, Key.of(trustAnchorId))
                        .ifPresent(trustAnchor -> {
                            rpkiRepositories.removeAllForTrustAnchor(tx, trustAnchor);
                            trustAnchors.remove(tx, trustAnchor);
                            validatedRpkiObjects.remove(trustAnchor);
                        }));
    }

    @PostConstruct
    public void managePreconfiguredAndExistingTrustAnchors() {
        log.info("Automatically adding preconfigured trust anchors");

        final File[] tals = preconfiguredTrustAnchorDirectory.listFiles(new PatternFilenameFilter(Pattern.compile("^.*\\.tal$")));
        if (ArrayUtils.isEmpty(tals)) {
            log.warn("No preconfigured trust anchors found at {}, skipping", preconfiguredTrustAnchorDirectory);
            return;
        }

        for (final File tal : tals) {
            final TrustAnchorLocator locator = TrustAnchorLocator.fromFile(tal);
            storage.writeTx0(tx -> {
                Optional<TrustAnchor> ta = trustAnchors.findBySubjectPublicKeyInfo(tx, locator.getPublicKeyInfo());
                if (ta.isPresent()) {
                    log.info("Preconfigured trust anchor '{}' already installed, skipping", locator.getCaName());
                } else {
                    TrustAnchor trustAnchor = new TrustAnchor(true);
                    trustAnchor.setName(locator.getCaName());
                    trustAnchor.setLocations(
                            locator.getCertificateLocations().stream()
                                    .map(URI::toASCIIString)
                                    .collect(Collectors.toList()));
                    trustAnchor.setSubjectPublicKeyInfo(locator.getPublicKeyInfo());
                    trustAnchor.setRsyncPrefetchUri(
                            locator.getPrefetchUris().stream()
                                    .filter(uri -> "rsync".equalsIgnoreCase(uri.getScheme()))
                                    .map(URI::toASCIIString)
                                    .findFirst().orElse(null)
                    );
                    add(tx, trustAnchor);
                }
            });
        }

        storage.readTx0(tx -> {
            log.info("Schedule validation for TAs that were in the database already");
            trustAnchors.findAll(tx).forEach(ta -> {
                trustAnchorState.setUnknown(ta);
                if (!validationScheduler.scheduledTrustAnchor(ta)) {
                    log.info("Adding {} to the validation scheduler", ta.getName());
                    validationScheduler.addTrustAnchor(ta);
                }
            });
        });
    }

}
