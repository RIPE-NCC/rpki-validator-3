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
package net.ripe.rpki.rtr.domain;

import lombok.ToString;
import lombok.Value;

@Value(staticConstructor = "of")
@ToString(includeFieldNames = false)
public class SerialNumber implements Comparable<SerialNumber> {
    public static final int SERIAL_BITS = 32;
    public static final long HALF_SERIAL_NUMBER_RANGE = (1L << (SERIAL_BITS - 1));

    private static final SerialNumber ZERO = SerialNumber.of((short) 0);

    private final int value;

    public static SerialNumber zero() {
        return ZERO;
    }

    public SerialNumber next() {
        return SerialNumber.of((short) (this.value + 1));
    }

    public SerialNumber previous() {
        return SerialNumber.of((short) (this.value - 1));
    }

    public boolean isBefore(SerialNumber that) {
        long i1 = Integer.toUnsignedLong(this.value);
        long i2 = Integer.toUnsignedLong(that.value);
        return (this.value != that.value && (
            i1 < i2 && (i2 - i1) < HALF_SERIAL_NUMBER_RANGE
                || i1 > i2 && (i1 - i2) > HALF_SERIAL_NUMBER_RANGE
        ));
    }

    public boolean isAfter(SerialNumber that) {
        long i1 = Integer.toUnsignedLong(this.value);
        long i2 = Integer.toUnsignedLong(that.value);
        return (this.value != that.value && (
            i1 < i2 && (i2 - i1) > HALF_SERIAL_NUMBER_RANGE
                || i1 > i2 && (i1 - i2) < HALF_SERIAL_NUMBER_RANGE
        ));
    }

    @Override
    public int compareTo(SerialNumber that) {
        if (this.equals(that)) {
            return 0;
        } else if (this.isBefore(that)) {
            return -1;
        } else if (this.isAfter(that)) {
            return 1;
        } else {
            return 0;
        }
    }

}
