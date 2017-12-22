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

import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.validator3.domain.RpkiRepositories;
import net.ripe.rpki.validator3.domain.RpkiRepository;
import net.ripe.rpki.validator3.domain.TrustAnchor;
import net.ripe.rpki.validator3.domain.TrustAnchors;
import net.ripe.rpki.validator3.domain.ValidationRuns;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import javax.transaction.Transactional;
import javax.validation.Valid;

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
    private ValidationRuns validationRunRepository;

    public long execute(@Valid AddTrustAnchor command) {
        TrustAnchor trustAnchor = new TrustAnchor();
        trustAnchor.setName(command.getName());
        trustAnchor.setLocations(command.getLocations());
        trustAnchor.setSubjectPublicKeyInfo(command.getSubjectPublicKeyInfo());

        if (command.getRsyncPrefetchUri() != null) {
            trustAnchor.setRsyncPrefetchUri(command.getRsyncPrefetchUri());
            rpkiRepositories.register(trustAnchor, command.getRsyncPrefetchUri(), RpkiRepository.Type.RSYNC_PREFETCH);
        }

        trustAnchors.add(trustAnchor);

        log.info("added trust anchor '{}'", trustAnchor);

        return trustAnchor.getId();
    }

    public void remove(long trustAnchorId) {
        TrustAnchor trustAnchor = trustAnchors.get(trustAnchorId);
        validationRunRepository.removeAllForTrustAnchor(trustAnchor);
        rpkiRepositories.removeAllForTrustAnchor(trustAnchor);
        trustAnchors.remove(trustAnchor);
    }

}
