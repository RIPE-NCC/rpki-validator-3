package net.ripe.rpki.validator3.util;

import org.apache.commons.lang3.tuple.Pair;

import java.util.function.Supplier;

public class Time {
    public static <T> Pair<T, Long> timed(Supplier<T> s) {
        long begin = System.currentTimeMillis();
        T t = s.get();
        long end = System.currentTimeMillis();
        return Pair.of(t, end - begin);
    }
}
