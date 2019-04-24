package net.ripe.rpki.validator3.storage.data.coders;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.IntStream;

@Data
@AllArgsConstructor
@EqualsAndHashCode
public class Encoded {
    @Data
    @AllArgsConstructor
    public static class HeaderEntry {
        private short fieldTag;
        private int offset;
        private int length;
    }

    private final Map<Short, byte[]> content = new HashMap<>();

    public void append(short fieldTag, byte[] bytes) {
        if (bytes != null) {
            content.put(fieldTag, bytes);
        }
    }

    public <T> void appendNotNull(T value, short fieldTag, Function<T, byte[]> f) {
        if (value != null) {
            content.put(fieldTag, f.apply(value));
        }
    }

    public byte[] toByteArray() {
        int totalSize = Integer.BYTES +
                content.values().stream()
                .map(b -> Short.BYTES + 2 * Integer.BYTES + b.length)
                .reduce(0, Integer::sum);

        byte[] array = new byte[totalSize];
        ByteBuffer bb = ByteBuffer.wrap(array);
        bb.putInt(content.size());

        int currentOffset = 0;
        final List<Map.Entry<Short, byte[]>> entries = new ArrayList<>(content.entrySet());

        for (Map.Entry<Short, byte[]> e : entries) {
            final Short fieldTag = e.getKey();
            final byte[] bytes = e.getValue();
            bb.putShort(fieldTag);
            bb.putInt(currentOffset);
            bb.putInt(bytes.length);
            currentOffset += bytes.length;
        }
        for (Map.Entry<Short, byte[]> e : entries) {
            bb.put(e.getValue());
        }
        return array;
    }

    public static Encoded fromByteArray(byte[] array) {
        final ByteBuffer bb = ByteBuffer.wrap(array);
        final int entryCount = bb.getInt();
        final List<HeaderEntry> entries = new ArrayList<>(entryCount);
        IntStream.range(0, entryCount).forEach(i -> {
            final short fieldTag = bb.getShort();
            final int offset = bb.getInt();
            final int length = bb.getInt();
            entries.add(new HeaderEntry(fieldTag, offset, length));
        });

        entries.sort(Comparator.comparing(e -> e.offset));
        final Encoded encoded = new Encoded();
        for (HeaderEntry e : entries) {
            byte[] bytes = new byte[e.length];
            bb.get(bytes);
            encoded.append(e.fieldTag, bytes);
        }
        return encoded;
    }

    public static Optional<byte[]> field(Map<Short, byte[]> c, short tag) {
        return Optional.ofNullable(c.get(tag));
    }

}
