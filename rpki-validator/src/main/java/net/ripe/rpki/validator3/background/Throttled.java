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

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * The purpose of this class is to be able to execute an arbitrary action
 * not more often than a certain interval. I.e.
 * <p>
 * val throttled = new Throttled(10)
 * <p>
 * throttled.trigger("action1", r) - will execute immediately.
 * <p>
 * Thread.sleep(3)
 * <p>
 * throttled.trigger("action1", r) - will schedule execution in 7 (i.e. 10 - 3) seconds from now.
 * throttled.trigger("action1", r) - will be ignored as there's already execution scheduled.
 * <p>
 * Thread.sleep(10)
 * <p>
 * throttled.trigger("action1", r) - will execute immediately again.
 */
public class Throttled<Key> {

    private final long minInterval;

    private final Map<Key, Action> actionMap = new HashMap<>();

    private final ScheduledExecutorService scheduledExecutor =
        Executors.newScheduledThreadPool(Runtime.getRuntime().availableProcessors());

    public Throttled(long minInterval) {
        this.minInterval = minInterval;
    }

    public void trigger(Key key, Runnable r) {
        final Runnable wrappedRunnable = () -> {
            try {
                r.run();
            } finally {
                final Instant after = Instant.now();
                synchronized (actionMap) {
                    actionMap.put(key, new Action(after, false));
                }
            }
        };

        final Instant now = Instant.now();
        synchronized (actionMap) {
            final Action action = actionMap.get(key);
            if (action != null) {
                if (!action.alreadyScheduled) {
                    final Instant lastTime = action.getLastTime();
                    final Duration between = Duration.between(lastTime, now);
                    if (between.getSeconds() < minInterval) {
                        final long delay = minInterval - between.getSeconds();
                        actionMap.put(key, action.createScheduled());
                        scheduledExecutor.schedule(wrappedRunnable, delay, TimeUnit.SECONDS);
                    } else {
                        actionMap.put(key, action.createScheduled());
                        scheduledExecutor.execute(wrappedRunnable);
                    }
                }
            } else {
                actionMap.put(key, new Action(now, true));
                scheduledExecutor.execute(wrappedRunnable);
            }
        }
    }

    @Data
    @AllArgsConstructor
    private static class Action {
        private Instant lastTime;
        private boolean alreadyScheduled;

        public Action createScheduled() {
            return new Action(lastTime, true);
        }
    }

}
