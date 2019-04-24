package net.ripe.rpki.validator3.storage.data.coders;

import com.google.common.primitives.Longs;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public class Coders {
    private static final Set<Short> uniqueTags = new HashSet<>();

    static synchronized short uniqueTag(int t) {
        final short tag = (short) t;
        if (uniqueTags.contains(tag)) {
            throw new RuntimeException("Tag " + tag + " is not unique.");
        }
        uniqueTags.add(tag);
        return tag;
    }

    public static byte[] toBytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    public static byte[] toBytes(Instant instant) {
        return Longs.toByteArray(instant.toEpochMilli());
    }

    public static byte[] toBytes(long long_) {
        return Longs.toByteArray(long_);
    }

    public static byte[] toBytes(BigInteger bi) {
        return bi.toByteArray();
    }

    static <R> byte[] toBytes(Collection<R> list, Function<R, byte[]> f) {
        final List<byte[]> bs = list.stream().map(f).collect(Collectors.toList());
        final int fullSize = Integer.BYTES + bs.stream()
                .map(b -> b.length + Integer.BYTES)
                .reduce(0, Integer::sum);

        final byte[] array = new byte[fullSize];
        final ByteBuffer bb = ByteBuffer.wrap(array);

        bb.putInt(bs.size());
        bs.forEach(b -> {
            bb.putInt(b.length);
            bb.put(b);
        });
        return array;
    }

    static <R> List<R> fromBytes(byte[] array, Function<byte[], R> c) {
        final ByteBuffer bb = ByteBuffer.wrap(array);
        final int fullSize = bb.getInt();
        final List<R> list = new ArrayList<>(fullSize);
        for (int i = 0; i < fullSize; i++) {
            int size = bb.getInt();
            byte[] bytes = new byte[size];
            bb.get(bytes);
            list.add(c.apply(bytes));
        }
        return list;
    }

    public static long toLong(byte[] bytes) {
        return Longs.fromByteArray(bytes);
    }

    public static int toInt(byte[] bytes) {
        return ByteBuffer.wrap(bytes).getInt();
    }

    public static Instant toInstant(byte[] bytes) {
        return Instant.ofEpochMilli(Longs.fromByteArray(bytes));
    }

    public static String toString(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8);
    }

    public static BigInteger toBigInteger(byte[] b) {
        return new BigInteger(b);
    }
}
