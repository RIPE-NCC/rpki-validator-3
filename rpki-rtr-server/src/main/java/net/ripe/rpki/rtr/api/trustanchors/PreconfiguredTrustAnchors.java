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
package net.ripe.rpki.rtr.api.trustanchors;

import com.google.common.io.PatternFilenameFilter;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.rtr.domain.Settings;
import net.ripe.rpki.rtr.domain.TrustAnchor;
import net.ripe.rpki.rtr.domain.TrustAnchors;
import net.ripe.rpki.rtr.util.TrustAnchorLocator;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.PostConstruct;
import java.io.File;
import java.net.URI;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
@Profile("!test")
@Slf4j
public class PreconfiguredTrustAnchors {
    public static final String PRECONFIGURED_TAL_SETTINGS_KEY = "preconfigured.tals.loaded";

    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private TrustAnchors trustAnchors;
    @Autowired
    private TrustAnchorService trustAnchorService;
    @Autowired
    private Settings settings;

    @Value("${rpki.validator.preconfigured.trust.anchors.directory}")
    private File preconfiguredTrustAnchorDirectory;

    @PostConstruct
    public void managePreconfiguredTrustAnchors() {
        new TransactionTemplate(transactionManager).execute((status) -> {
            log.info("Automatically adding preconfigured trust anchors");

            if ("true".equals(settings.get(PRECONFIGURED_TAL_SETTINGS_KEY).orElse("false"))) {
                log.info("Preconfigured trust anchors are already loaded, skipping");
                return null;
            }

            settings.put(PRECONFIGURED_TAL_SETTINGS_KEY, "true");

            File[] tals = preconfiguredTrustAnchorDirectory.listFiles(new PatternFilenameFilter(Pattern.compile("^.*\\.tal$")));
            if (ArrayUtils.isEmpty(tals)) {
                log.warn("No preconfigured trust anchors found at {}, skipping", preconfiguredTrustAnchorDirectory);
                return null;
            }

            for (File tal : tals) {
                TrustAnchorLocator locator = TrustAnchorLocator.fromFile(tal);
                if (trustAnchors.findBySubjectPublicKeyInfo(locator.getPublicKeyInfo()).isPresent()) {
                    log.info("Preconfigured trust anchor '{}' already installed, skipping", locator.getCaName());
                    continue;
                }

                AddTrustAnchor command = AddTrustAnchor.builder()
                    .type(TrustAnchor.TYPE)
                    .name(locator.getCaName())
                    .locations(locator.getCertificateLocations().stream().map(URI::toASCIIString).collect(Collectors.toList()))
                    .subjectPublicKeyInfo(locator.getPublicKeyInfo())
                    .rsyncPrefetchUri(locator.getPrefetchUris().stream()
                        .filter(uri -> "rsync".equalsIgnoreCase(uri.getScheme()))
                        .map(URI::toASCIIString)
                        .findFirst().orElse(null)
                    )
                    .build();
                trustAnchorService.execute(command);
            }

            return null;
        });
    }
}
