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
