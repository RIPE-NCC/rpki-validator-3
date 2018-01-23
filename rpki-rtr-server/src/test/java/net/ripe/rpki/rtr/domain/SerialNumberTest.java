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

import org.junit.Test;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

public class SerialNumberTest {

    private final static Random RANDOM = new Random();

    @Test
    public void should_be_equal() {
        assertThat(SerialNumber.of(0)).isEqualTo(SerialNumber.of(0));
        assertThat(SerialNumber.of(Integer.MAX_VALUE)).isEqualTo(SerialNumber.of(Integer.MAX_VALUE));
        assertThat(SerialNumber.of(Integer.MIN_VALUE)).isEqualTo(SerialNumber.of(Integer.MIN_VALUE));

        for (int i = 0; i < 100; ++i) {
            SerialNumber n = SerialNumber.of(RANDOM.nextInt());
            assertThat(n).isEqualTo(n);
            assertThat(n).isEqualTo(SerialNumber.of(n.getValue()));
            assertThat(n).isNotEqualTo(n.next());
            assertThat(n).isNotEqualTo(n.previous());
        }
    }

    @Test
    public void should_be_before_not_after() {
        assertThat(SerialNumber.of(0).isBefore(SerialNumber.of(0))).isFalse();
        assertThat(SerialNumber.of(Integer.MAX_VALUE).isBefore(SerialNumber.of(Integer.MAX_VALUE))).isFalse();

        assertBefore(SerialNumber.of(0), SerialNumber.of(1));
        assertBefore(SerialNumber.of(Integer.MAX_VALUE - 1), SerialNumber.of(Integer.MAX_VALUE));
        assertBefore(SerialNumber.of(Integer.MIN_VALUE - 1), SerialNumber.of(Integer.MIN_VALUE));

        for (int i = 0; i < 1000; ++i) {
            int n = RANDOM.nextInt();
            assertThat(SerialNumber.of(n).isBefore(SerialNumber.of(n))).isFalse();
            assertBefore(SerialNumber.of(n), SerialNumber.of(n + 1));
            assertBefore(SerialNumber.of(n - 1), SerialNumber.of(n));
            assertBefore(SerialNumber.of(n), SerialNumber.of(n + Integer.MAX_VALUE));

            int d = 1 + RANDOM.nextInt((int) (SerialNumber.HALF_SERIAL_NUMBER_RANGE - 1));
            assertBefore(SerialNumber.of(n - d), SerialNumber.of(n));
            assertBefore(SerialNumber.of(n), SerialNumber.of(n + d));
        }
    }

    @Test
    public void should_be_after() {
        assertThat(SerialNumber.of(0).isBefore(SerialNumber.of(0))).isFalse();
        assertThat(SerialNumber.of(Integer.MAX_VALUE).isAfter(SerialNumber.of(Integer.MAX_VALUE))).isFalse();

        assertAfter(SerialNumber.of(1), SerialNumber.of(0));
        assertAfter(SerialNumber.of(Integer.MAX_VALUE + 1), SerialNumber.of(Integer.MAX_VALUE));
        assertAfter(SerialNumber.of(Integer.MIN_VALUE + 1), SerialNumber.of(Integer.MIN_VALUE));

        for (int i = 0; i < 1000; ++i) {
            int n = RANDOM.nextInt();
            assertThat(SerialNumber.of(n).isAfter(SerialNumber.of(n))).isFalse();
            assertAfter(SerialNumber.of(n), SerialNumber.of(n - 1));
            assertAfter(SerialNumber.of(n + 1), SerialNumber.of(n));
            assertAfter(SerialNumber.of(n), SerialNumber.of(n - Integer.MAX_VALUE));

            int d = 1 + RANDOM.nextInt((int) (SerialNumber.HALF_SERIAL_NUMBER_RANGE - 1));
            assertAfter(SerialNumber.of(n + d), SerialNumber.of(n));
            assertAfter(SerialNumber.of(n), SerialNumber.of(n - d));
        }
    }

    private static void assertBefore(SerialNumber s1, SerialNumber s2) {
        assertThat(s1).isLessThan(s2);

        String message = s1.getValue() + " < " + s2.getValue();
        assertThat(s1.isBefore(s2)).as("isBefore, but %s", message).isTrue();
        assertThat(s1.equals(s2)).as("equal, but %s", message).isFalse();
        assertThat(s1.isAfter(s2)).as("isBefore, but %s", message).isFalse();
    }

    private static void assertAfter(SerialNumber s1, SerialNumber s2) {
        assertThat(s1).isGreaterThan(s2);

        String message = s1.getValue() + " > " + s2.getValue();
        assertThat(s1.isBefore(s2)).as("isBefore, but %s", message).isFalse();
        assertThat(s1.equals(s2)).as("equal, but %s", message).isFalse();
        assertThat(s1.isAfter(s2)).as("isBefore, but %s", message).isTrue();
    }
}
