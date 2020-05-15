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
package net.ripe.rpki.validator3.domain.validation;

import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.validator3.background.ValidationScheduler;
import net.ripe.rpki.validator3.storage.data.TrustAnchor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * It is to keep track of the state of a TA. A TA transitions to the VALIDATED when
 * and only when the data for it has been downloaded and the tree validation has been
 * performed.
 */
@Component
@Slf4j
public class TrustAnchorState {

    private enum State {
        UNKNOWN,
        VALIDATED
    }

    private final Map<String, State> states = new HashMap<>();
    private final Map<String, Instant> lastTreeValidationTime = new HashMap<>();

    private final long minIntervalBetweenTreeValidationsInSeconds;

    @Autowired
    public TrustAnchorState(final ValidationScheduler validationScheduler) {
        // Allow tre re-validation as often as minimum repository fetch interval,
        // but in any case not more often then once a minute.
        minIntervalBetweenTreeValidationsInSeconds = Math.max(60,
            Math.min(
                validationScheduler.getRsyncRepositoryDownloadInterval().getSeconds(),
                validationScheduler.getRrpdRepositoryDownloadInterval().getSeconds()));
    }

    public boolean allTAsValidatedAfterRepositoryLoading() {
        synchronized (states) {
            return states.values().stream().allMatch(s -> s.equals(State.VALIDATED));
        }
    }

    public void setUnknown(TrustAnchor ta) {
        setState(ta, State.UNKNOWN);
    }

    public void setValidatedAfterLastRepositoryUpdate(TrustAnchor ta) {
        setState(ta, State.VALIDATED);
    }

    private void setState(TrustAnchor ta, State state) {
        log.debug("Setting TA {} to {}", ta.getName(), state);
        synchronized (states) {
            states.put(ta.getName(), state);
        }
    }

    /**
     * This is to prevent validating CA too often and consume too much CPU.
     *
     * CertificateTreeValidationJob is triggered every time there's an update of a repository related
     * to a TA, so the more delegated CAs are in the TA, the more often tree re-validation is happening.
     *
     * To prevent this, re-validate the tree only if it's not been validated some time ago.
     */
    public void throttledValidate(TrustAnchor ta, Consumer<TrustAnchor> f) {
        Duration timeSinceTheLastTime = null;
        final Instant now = Instant.now();
        synchronized (lastTreeValidationTime) {
            final Instant lastTime = lastTreeValidationTime.get(ta.getName());
            if (lastTime != null) {
                final Duration between = Duration.between(lastTime, now);
                if (between.getSeconds() < minIntervalBetweenTreeValidationsInSeconds) {
                    timeSinceTheLastTime = between;
                }
            }
        }
        if (timeSinceTheLastTime == null) {
            try {
                f.accept(ta);
            } finally {
                final Instant after = Instant.now();
                synchronized (lastTreeValidationTime) {
                    lastTreeValidationTime.put(ta.getName(), after);
                }
            }
        } else {
            log.debug("Not going to re-validate the CA tree for TA {}, validated {}s ago", ta.getName(),
                timeSinceTheLastTime.getSeconds());
        }
    }

}
