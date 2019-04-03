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

import com.google.common.collect.ImmutableList;
import com.google.common.io.PatternFilenameFilter;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.validator3.background.ValidationScheduler;
import net.ripe.rpki.validator3.storage.data.Key;
import net.ripe.rpki.validator3.storage.Lmdb;
import net.ripe.rpki.validator3.storage.data.RpkiRepository;
import net.ripe.rpki.validator3.storage.data.TrustAnchor;
import net.ripe.rpki.validator3.storage.lmdb.Tx;
import net.ripe.rpki.validator3.storage.stores.RpkiRepositoryStore;
import net.ripe.rpki.validator3.storage.stores.SettingsStore;
import net.ripe.rpki.validator3.storage.stores.TrustAnchorStore;
import net.ripe.rpki.validator3.storage.stores.ValidationRunStore;
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
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
@Validated
@Slf4j
public class TrustAnchorService {

    private final TrustAnchorStore trustAnchorStore;

    private final RpkiRepositoryStore rpkiRepositoryStore;

    private final ValidationRunStore validationRunStore;

    private final SettingsStore settingsStore;

    private final ValidationScheduler validationScheduler;

    @Value("${rpki.validator.preconfigured.trust.anchors.directory}")
    private File preconfiguredTrustAnchorDirectory;

    private final Lmdb lmdb;

    @Autowired
    public TrustAnchorService(TrustAnchorStore trustAnchorStore,
                              RpkiRepositoryStore rpkiRepositoryStore,
                              ValidationRunStore validationRunStore,
                              SettingsStore settingsStore,
                              ValidationScheduler validationScheduler,
                              Lmdb lmdb) {
        this.trustAnchorStore = trustAnchorStore;
        this.rpkiRepositoryStore = rpkiRepositoryStore;
        this.validationRunStore = validationRunStore;
        this.settingsStore = settingsStore;
        this.validationScheduler = validationScheduler;
        this.lmdb = lmdb;
    }

    public long execute(@Valid AddTrustAnchor command) {
        TrustAnchor trustAnchor = new TrustAnchor(false);
        trustAnchor.setName(command.getName());
        trustAnchor.setLocations(ImmutableList.copyOf(command.getLocations()));
        trustAnchor.setSubjectPublicKeyInfo(command.getSubjectPublicKeyInfo());
        trustAnchor.setRsyncPrefetchUri(command.getRsyncPrefetchUri());
        return add(lmdb.writeTx(), trustAnchor);
    }

    long add(Tx.Write tx, TrustAnchor trustAnchor) {
        return Tx.with(tx, tx1 -> {
            if (trustAnchor.getRsyncPrefetchUri() != null) {
                rpkiRepositoryStore.register(tx1, trustAnchor,
                        trustAnchor.getRsyncPrefetchUri(), RpkiRepository.Type.RSYNC_PREFETCH);
            }

            trustAnchorStore.add(tx1, trustAnchor);
            log.info("Added trust anchor '{}'", trustAnchor);

            return trustAnchor.getId().asLong();
        });
    }

    public void remove(long trustAnchorId) {
        Tx.use(lmdb.writeTx(), tx -> {
            Optional<TrustAnchor> trustAnchor1 = trustAnchorStore.get(tx, Key.of(trustAnchorId));
            trustAnchor1.ifPresent(trustAnchor -> {
                rpkiRepositoryStore.removeAllForTrustAnchor(tx, trustAnchor);
                trustAnchorStore.remove(tx, trustAnchor);
                // TODO Fix that
//                validatedRpkiObjects.remove(tx, trustAnchor);
            });
        });
    }

    @PostConstruct
    public void managePreconfiguredAndExistingTrustAnchors() {
        log.info("Automatically adding preconfigured trust anchors");

        final Boolean alreadyLoaded = Tx.with(lmdb.writeTx(), tx -> {
            if (settingsStore.isPreconfiguredTalsLoaded(tx)) {
                log.info("Preconfigured trust anchors are already loaded, skipping");
                scheduleTasValidation(tx);
                return false;
            }
            settingsStore.markPreconfiguredTalsLoaded(tx);
            return true;
        });

        if (alreadyLoaded) {
            return;
        }

        final File[] tals = preconfiguredTrustAnchorDirectory.listFiles(new PatternFilenameFilter(Pattern.compile("^.*\\.tal$")));
        if (ArrayUtils.isEmpty(tals)) {
            log.warn("No preconfigured trust anchors found at {}, skipping", preconfiguredTrustAnchorDirectory);
            return;
        }

        for (final File tal : tals) {
            final TrustAnchorLocator locator = TrustAnchorLocator.fromFile(tal);
            Tx.use(lmdb.writeTx(), tx -> {
                Optional<TrustAnchor> ta = trustAnchorStore.findBySubjectPublicKeyInfo(tx, locator.getPublicKeyInfo());
                if (ta.isPresent()) {
                    log.info("Preconfigured trust anchor '{}' already installed, skipping", locator.getCaName());
                } else {
                    TrustAnchor trustAnchor = new TrustAnchor(true);
                    trustAnchor.setName(locator.getCaName());
                    trustAnchor.setLocations(ImmutableList.copyOf(
                            locator.getCertificateLocations().stream()
                                    .map(URI::toASCIIString)
                                    .collect(Collectors.toList())
                    ));
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

        scheduleTasValidation(lmdb.writeTx());
    }

    private void scheduleTasValidation(Tx.Write tx) {
        log.info("Schedule TA validation that were in the database already");
        trustAnchorStore.findAll(tx).forEach(ta -> {
            if (!validationScheduler.scheduledTrustAnchor(ta)) {
                log.info("Adding " + ta.getName() + " to the validation scheduler");
                validationScheduler.addTrustAnchor(ta);
            }
        });
    }
}
