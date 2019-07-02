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

import lombok.AllArgsConstructor;
import lombok.Data;
import org.jetbrains.annotations.NotNull;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class Bench {

    private String namespace;

    private Bench(String namespace) {
        this.namespace = namespace;
    }

    public static Bench create(String namespace) {
        return new Bench(namespace);
    }

    private static Map<String, Bench> benches  = new HashMap<>();

    public static <T> T mark(String namespace, String tag, Supplier<T> s) {
        return getBench(namespace).measure(tag, s);
    }

    public static void mark0(String namespace, String tag, Runnable r) {
        getBench(namespace).measure0(tag, r);
    }

    public static <T> T mark(String tag, Supplier<T> s) {
        return mark("global", tag, s);
    }

    public static void mark0(String tag, Runnable r) {
        mark0("global", tag, r);
    }

    @NotNull
    private static synchronized Bench getBench(String namespace) {
        Bench bench = benches.get(namespace);
        if (bench == null) {
            bench = new Bench(namespace);
            benches.put(namespace, bench);
        }
        return bench;
    }

    @Data
    @AllArgsConstructor
    private static class Record {
        long count;
        long totalTime;
        long minTime;
        long maxTime;
    }

    private final Map<String, Record> records = new HashMap<>();

    private void measure0(String tag, Runnable r) {
        measure(tag, () -> {
            r.run();
            return null;
        });
    }

    public <T> T measure(String tag, Supplier<T> s) {
        final T v;
        long b = System.nanoTime();
        v = s.get();
        long e = System.nanoTime();
        final long time = e - b;
        synchronized (records) {
            final Record r = records.get(tag);
            if (r == null) {
                records.put(tag, new Record(1, time, time, time));
            } else {
                records.put(tag, new Record(
                    r.count + 1,
                    r.totalTime + time,
                    Math.min(r.minTime, time),
                    Math.max(r.maxTime, time)));
            }
        }
        return v;
    }

    public static String dump(String namespace) {
        return getBench(namespace).dump();
    }

    private String dump() {
        synchronized (records) {
            final String s = records.entrySet().stream()
                .sorted(Comparator.comparing(Map.Entry::getKey))
                .map(e -> {
                    final Record r = e.getValue();

                    StringBuilder sb = new StringBuilder(namespace).append("# ").append(e.getKey()).append(": ");
                    sb.append("max = ").append(asMs(r.maxTime));
                    sb.append(", min = ").append(asMs(r.minTime));
                    sb.append(", full total = ").append(asMs(r.totalTime));
                    sb.append(", count = ").append(r.count);
                    sb.append(", avg = ").append(avgAsMs(r.totalTime, r.count));
                    return sb.toString();
                })
                .collect(Collectors.joining("\n"));
            records.clear();
            return s;
        }
    }

    private static String avgAsMs(long total, long n) {
        final NumberFormat formatter = new DecimalFormat("#0.00");
        return formatter.format((total/1000_000.0)/n);
    }

    private static String asMs(long nanoSeconds) {
        final NumberFormat formatter = new DecimalFormat("#0.00");
        return formatter.format(nanoSeconds/1000_000.0);
    }
}
