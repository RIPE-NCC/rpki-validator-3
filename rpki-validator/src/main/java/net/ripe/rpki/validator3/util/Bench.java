package net.ripe.rpki.validator3.util;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class Bench {

    @Data
    @AllArgsConstructor
    private static class Record {
        long count;
        long totalTime;
        long minTime;
        long maxTime;
    }

    private static final Map<String, Record> records = new HashMap<>();

    public static <T> T mark(String tag, Supplier<T> s) {
        final Pair<T, Long> timed = Time.timed(s);
        final Long time = timed.getRight();
        synchronized (records) {
            final Record record = records.get(tag);
            if (record == null) {
                records.put(tag, new Record(1, time, time, time));
            } else {
                records.put(tag, new Record(
                    record.count + 1,
                    record.totalTime + time,
                    Math.min(record.minTime, time),
                    Math.max(record.maxTime, time)));
            }
        }
        return timed.getLeft();
    }

    public static String dump() {
        synchronized (records) {
            final String s = records.entrySet().stream()
                .sorted(Comparator.comparing(Map.Entry::getKey))
                .map(e -> e.getKey() + ": " + e.getValue())
                .collect(Collectors.joining("\n"));
            records.clear();
            return s;
        }
    }
}
