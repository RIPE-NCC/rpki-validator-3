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
package net.ripe.rpki.validator3.config.background;

import lombok.Data;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.listeners.JobListenerSupport;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.Map;
import java.util.TreeMap;

@Component
public class BackgroundJobInfo extends JobListenerSupport {

    @Data(staticConstructor = "of")
    public static class Execution {
        public final Date started;
        public final Date finished;
        public final long count;

        static Execution again(Execution e) {
            if (e == null) {
                return of(new Date(), null, 1);
            }
            return of(e.started, e.finished, e.count + 1);
        }

        static Execution finish(Execution e) {
            return of(e.started, new Date(), e.count);
        }
    }

    private final TreeMap<String, Execution> backgroundJobStats = new TreeMap<>();

    @Override
    public void jobToBeExecuted(JobExecutionContext context) {
        final String key = context.getJobDetail().getKey().toString();
        synchronized (backgroundJobStats) {
            final Execution execution = backgroundJobStats.get(key);
            backgroundJobStats.put(key, Execution.again(execution));
        }
    }

    @Override
    public void jobWasExecuted(JobExecutionContext context, JobExecutionException jobException) {
        final String key = context.getJobDetail().getKey().toString();
        synchronized (backgroundJobStats) {
            final Execution execution = backgroundJobStats.get(key);
            if (execution != null) {
                backgroundJobStats.put(key, Execution.finish(execution));
            }
        }
    }

    @Override
    public String getName() {
        return "Stat collector";
    }

    public Map<String, Execution> getStat() {
        synchronized (backgroundJobStats) {
            return backgroundJobStats;
        }
    }
}
