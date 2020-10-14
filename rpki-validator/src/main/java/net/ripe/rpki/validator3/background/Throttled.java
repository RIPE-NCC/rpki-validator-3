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
import lombok.Value;

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
 * val throttled = new Throttled(10_000)
 * <p>
 * throttled.trigger("action1", r1) - will execute r1 immediately.
 * <p>
 * Thread.sleep(3000)
 * <p>
 * throttled.trigger("action1", r2) - will schedule execution of r2 in 7 (i.e. 10 - 3) seconds from now.
 * throttled.trigger("action1", r3) - will replace scheduled execution with r3.
 * <p>
 * Thread.sleep(7010);
 * throttled.trigger("action1", r4) - executes r4 after the currently running action completes ensuring the minimum delay.
 * <p>
 * Thread.sleep(11000)
 * <p>
 * throttled.trigger("action1", r5) - will execute r5 immediately again.
 */
public class Throttled<Key> {

    private final long minIntervalMs;

    /*
     * Tracks the action for each Key. This map is used concurrently by multiple threads
     * so must be locked for any access. This might become a bottleneck when there are many
     * keyed tasks, but currently this is not the case.
     */
    private final Map<Key, Action> actionMap = new HashMap<>();

    private final ScheduledExecutorService scheduledExecutor =
        Executors.newScheduledThreadPool(Runtime.getRuntime().availableProcessors());

    public Throttled(long minIntervalMs) {
        this.minIntervalMs = minIntervalMs;
    }

    public void trigger(Key key, Runnable r) {
        synchronized (actionMap) {
            final Instant now = Instant.now();
            final Action action = actionMap.get(key);

            if (action == null) {
                // New key, start running action as soon as possible.
                actionMap.put(key, Action.toBeExecutedASAP(now, r));
                scheduledExecutor.execute(actionRunner(key));
            } else if (action.running) {
                // Action for this key is currently running, mark the
                // action as scheduled so that it will be started again
                // as soon as the current one finishes.
                actionMap.put(key, action.scheduled(r));
            } else if (action.alreadyScheduled) {
                // Action already scheduled for this key, only replace
                // the task that needs to be run when it starts.
                actionMap.put(key, action.replaced(r));
            } else {
                // Action not running or scheduled, so schedule it for
                // execution taking into account the minIntervalMs.
                // Note that a negative delay will schedule the action for
                // immediate execution.
                final Instant lastTime = action.getExecutionTime();
                final long delay = minIntervalMs - (now.toEpochMilli() - lastTime.toEpochMilli());
                actionMap.put(key, action.scheduled(r));
                scheduledExecutor.schedule(actionRunner(key), delay, TimeUnit.MILLISECONDS);
            }
        }
    }

    private Runnable actionRunner(Key key) {
        return () -> {
            try {
                Action action;
                synchronized (actionMap) {
                    action = actionMap.get(key);
                    actionMap.put(key, action.started(Instant.now()));
                }
                action.runnable.run();
            } finally {
                synchronized (actionMap) {
                    Action action = actionMap.get(key);
                    actionMap.put(key, action.finished());
                    if (action.alreadyScheduled) {
                        trigger(key, action.runnable);
                    }
                }
            }
        };
    }

    @Value
    @AllArgsConstructor
    private static class Action {
        // Time of the start of the last execution or the time when it was scheduled for immediate execution.
        Instant executionTime;
        // Action is scheduled to run using the scheduled, or if already running, when the current execution finishes.
        boolean alreadyScheduled;
        // True this action is currently executing, false otherwise.
        boolean running;
        // The code to run when the next execution starts.
        Runnable runnable;

        private static Action toBeExecutedASAP(Instant executionTime, Runnable runnable) {
            return new Action(executionTime, false, false, runnable);
        }

        private Action started(Instant executionTime) {
            return new Action(executionTime, false, true, this.runnable);
        }

        private Action finished() {
            return new Action(this.executionTime, false, false, this.runnable);
        }

        private Action scheduled(Runnable runnable) {
            return new Action(this.executionTime, true, this.running, runnable);
        }

        private Action replaced(Runnable runnable) {
            return new Action(this.executionTime, this.alreadyScheduled, this.running, runnable);
        }
    }
}
