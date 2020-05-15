package net.ripe.rpki.validator3.background;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class ThrottledTest {
    @Test
    public void testTriggerDoenstTriggerTooOften() throws InterruptedException {
        AtomicInteger counter = new AtomicInteger(0);
        final Throttled<String> throttled = new Throttled<>(10);

        throttled.trigger("x", counter::incrementAndGet);
        waitALittleToAllowExecutorToProcessRunnables();
        assertEquals(1, counter.get());

        for (int i = 0; i < 10; i++) {
            throttled.trigger("x", counter::incrementAndGet);
        }
        Thread.sleep(1100);
        assertEquals(1, counter.get());
    }

    @Test
    public void testTriggerDoesTriggerAfterInterval() throws InterruptedException {
        AtomicInteger counter = new AtomicInteger(0);
        final Throttled<String> throttled = new Throttled<>(1);

        throttled.trigger("x", counter::incrementAndGet);
        waitALittleToAllowExecutorToProcessRunnables();
        assertEquals(1, counter.get());

        Thread.sleep(2000);
        throttled.trigger("x", counter::incrementAndGet);
        waitALittleToAllowExecutorToProcessRunnables();
        assertEquals(2, counter.get());
    }

    private void waitALittleToAllowExecutorToProcessRunnables() throws InterruptedException {
        Thread.sleep(100);
    }

}