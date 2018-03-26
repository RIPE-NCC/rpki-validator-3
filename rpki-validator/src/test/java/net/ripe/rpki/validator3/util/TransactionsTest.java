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
package net.ripe.rpki.validator3.util;

import net.ripe.rpki.validator3.IntegrationTest;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

@RunWith(SpringRunner.class)
@IntegrationTest
public class TransactionsTest {

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transactionTemplate;

    @Before
    public void setUp() {
        transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Test
    public void should_run_hook_after_commit() {
        AtomicBoolean hookWasExecuted = new AtomicBoolean(false);

        transactionTemplate.execute((status) -> {
            Transactions.afterCommit(() -> hookWasExecuted.set(true));
            return null;
        });

        assertThat(hookWasExecuted.get()).isTrue();
    }

    @Test
    public void should_not_run_hook_after_rollback() {
        AtomicBoolean hookWasExecuted = new AtomicBoolean(false);

        transactionTemplate.execute((status) -> {
            Transactions.afterCommit(() -> hookWasExecuted.set(true));
            Transactions.afterCommitOnce("key", () -> hookWasExecuted.set(true));
            status.setRollbackOnly();
            return null;
        });

        assertThat(hookWasExecuted.get()).isFalse();
    }

    @Test
    public void should_run_hook_once_per_key_after_commit() {
        AtomicInteger hookExecutionCount = new AtomicInteger(0);

        transactionTemplate.execute((status) -> {
            Transactions.afterCommit(() -> assertThat(hookExecutionCount.incrementAndGet()).isEqualTo(1));
            Transactions.afterCommitOnce("A", () -> assertThat(hookExecutionCount.incrementAndGet()).isEqualTo(2));
            Transactions.afterCommitOnce("A", () -> fail("hook `A` executed again"));
            Transactions.afterCommitOnce("B", () -> assertThat(hookExecutionCount.incrementAndGet()).isEqualTo(3));
            Transactions.afterCommit(() -> assertThat(hookExecutionCount.incrementAndGet()).isEqualTo(4));
            Transactions.afterCommitOnce("B", () -> fail("hook `B` executed again"));
            Transactions.afterCommitOnce("A", () -> fail("hook `A` executed again"));
            return null;
        });

        assertThat(hookExecutionCount.get()).isEqualTo(4);
    }
}
