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

import lombok.SneakyThrows;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ThrottledTest {

    private final AtomicInteger counter = new AtomicInteger(0);

    private final Runnable slowIncrement = () -> {
        waitATinyBit();
        counter.incrementAndGet();
    };

    @Test
    public void testTriggerDoesntTriggerTooOften() throws InterruptedException {
        counter.set(0);
        final Throttled<String> throttled = new Throttled<>(10_000);
        throttled.trigger("x", slowIncrement);
        assertEquals(0, counter.get());
        throttled.trigger("x", slowIncrement);
        assertEquals(0, counter.get());

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
        counter.set(0);
        final Throttled<String> throttled = new Throttled<>(1000);

        throttled.trigger("x", slowIncrement);
        waitALittleToAllowExecutorToProcessRunnables();
        assertEquals(1, counter.get());

        Thread.sleep(2000);
        throttled.trigger("x", slowIncrement);
        waitALittleToAllowExecutorToProcessRunnables();
        assertEquals(2, counter.get());
    }

    @Test
    public void trigger_during_execution_should_run_action_immediately_after_finishing_current_execution() {
        final Throttled<String> throttled = new Throttled<>(200);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch triggeredDuringExecution = new CountDownLatch(1);
        CountDownLatch completed = new CountDownLatch(1);

        throttled.trigger("x", () -> {
            started.countDown();
            await(triggeredDuringExecution);
        });

        await(started);

        throttled.trigger("x", () -> completed.countDown());
        triggeredDuringExecution.countDown();

        assertTrue(await(completed));
    }

    @Test
    public void trigger_during_wait_replaces_runnable() {
        final Throttled<String> throttled = new Throttled<>(200);
        CountDownLatch firstTerminated = new CountDownLatch(1);
        CountDownLatch replacedTaskRan = new CountDownLatch(1);
        CountDownLatch replacementTaskRan = new CountDownLatch(1);

        throttled.trigger("x", () -> {
            firstTerminated.countDown();
        });

        await(firstTerminated);
        waitATinyBit();

        throttled.trigger("x", replacedTaskRan::countDown);
        throttled.trigger("x", replacementTaskRan::countDown);

        assertTrue("replacement task ran", await(replacementTaskRan));
        assertFalse("replaced task ran", await(replacedTaskRan));
    }

    @SneakyThrows
    private void waitALittleToAllowExecutorToProcessRunnables() {
        Thread.sleep(100);
    }

    @SneakyThrows
    private void waitATinyBit() {
        Thread.sleep(30);
    }

    @SneakyThrows
    private boolean await(CountDownLatch latch) {
        return latch.await(500, TimeUnit.MILLISECONDS);
    }
}
