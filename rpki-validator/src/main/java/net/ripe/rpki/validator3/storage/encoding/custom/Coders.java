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
package net.ripe.rpki.validator3.storage.encoding.custom;

import com.google.common.primitives.Longs;
import net.ripe.rpki.validator3.api.util.InstantWithoutNanos;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

public class Coders {

    public static byte[] toBytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    public static byte[] toBytes(InstantWithoutNanos instant) {
        return Longs.toByteArray(instant.toEpochMilli());
    }

    public static byte[] toBytes(int i) {
        ByteBuffer bb = ByteBuffer.allocate(Integer.BYTES);
        bb.putInt(i);
        return bb.array();
    }

    public static byte[] toBytes(short i) {
        ByteBuffer bb = ByteBuffer.allocate(Short.BYTES);
        bb.putShort(i);
        return bb.array();
    }

    public static byte[] toBytes(long long_) {
        return Longs.toByteArray(long_);
    }

    public static byte[] toBytes(BigInteger bi) {
        return bi.toByteArray();
    }

    public static <R> byte[] toBytes(Collection<R> list, Function<R, byte[]> f) {
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

    public static <R> List<R> fromBytes(byte[] array, Function<byte[], R> c) {
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

    public static short toShort(byte[] bytes) {
        return ByteBuffer.wrap(bytes).getShort();
    }

    public static InstantWithoutNanos toInstant(byte[] bytes) {
        return InstantWithoutNanos.ofEpochMilli(Longs.fromByteArray(bytes));
    }

    public static String toString(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8);
    }

    public static BigInteger toBigInteger(byte[] b) {
        return new BigInteger(b);
    }

    public static byte[] toBytes(Boolean b) {
        byte[] bytes = new byte[1];
        bytes[0] = (byte)(b ? 1 : 0);
        return bytes;
    }

    public static boolean toBoolean(byte[] b) {
        return b[0] == (byte)1;
    }
}
