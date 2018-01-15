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

import java.util.Arrays;
import java.util.Comparator;

import static org.assertj.core.api.Assertions.assertThat;

public class VersionedSetTest {

    private final VersionedSet<Integer> subject = new VersionedSet<>(Comparator.<Integer>naturalOrder(), 0L);

    @Test
    public void should_track_serial_number() {
        assertThat(subject.getCurrentVersion()).isEqualTo(0);

        subject.updateValues(Arrays.asList(1, 2, 3));
        assertThat(subject.getCurrentVersion()).isEqualTo(1);

        subject.updateValues(Arrays.asList(2, 3, 4));
        assertThat(subject.getCurrentVersion()).isEqualTo(2);
    }

    @Test
    public void should_contain_latest_values() {
        subject.updateValues(Arrays.asList(1, 2, 3));

        assertThat(subject.getValues()).containsExactly(1, 2, 3);
    }

    @Test
    public void should_track_deltas_between_updates() {
        subject.updateValues(Arrays.asList(1, 2, 3));
        assertThat(subject.getDelta(0).get().getAdditions()).containsExactly(1, 2, 3);

        subject.updateValues(Arrays.asList(2, 3, 4));
        assertThat(subject.getDelta(1).get().getAdditions()).containsExactly(4);
        assertThat(subject.getDelta(1).get().getRemovals()).containsExactly(1);

        subject.updateValues(Arrays.asList(3, 4, 5));
        assertThat(subject.getDelta(2).get().getAdditions()).containsExactly(5);
        assertThat(subject.getDelta(2).get().getRemovals()).containsExactly(2);
        assertThat(subject.getDelta(1).get().getAdditions()).containsExactly(4, 5);
        assertThat(subject.getDelta(1).get().getRemovals()).containsExactly(1, 2);

        subject.updateValues(Arrays.asList(4, 5, 6));
        assertThat(subject.getDelta(3).get().getAdditions()).containsExactly(6);
        assertThat(subject.getDelta(3).get().getRemovals()).containsExactly(3);
        assertThat(subject.getDelta(2).get().getAdditions()).containsExactly(5, 6);
        assertThat(subject.getDelta(2).get().getRemovals()).containsExactly(2, 3);
        assertThat(subject.getDelta(1).get().getAdditions()).containsExactly(4, 5, 6);
        assertThat(subject.getDelta(1).get().getRemovals()).containsExactly(1, 2, 3);

        subject.updateValues(Arrays.asList(1, 2, 3));
        assertThat(subject.getDelta(4).get().getAdditions()).containsExactly(1, 2, 3);
        assertThat(subject.getDelta(4).get().getRemovals()).containsExactly(4, 5, 6);
        assertThat(subject.getDelta(3).get().getAdditions()).containsExactly(1, 2);
        assertThat(subject.getDelta(3).get().getRemovals()).containsExactly(4, 5);
        assertThat(subject.getDelta(2).get().getAdditions()).containsExactly(1);
        assertThat(subject.getDelta(2).get().getRemovals()).containsExactly(4);
        assertThat(subject.getDelta(1).get().getAdditions()).containsExactly();
        assertThat(subject.getDelta(1).get().getRemovals()).containsExactly();

        subject.updateValues(Arrays.asList());
        assertThat(subject.getDelta(5).get().getAdditions()).containsExactly();
        assertThat(subject.getDelta(5).get().getRemovals()).containsExactly(1, 2, 3);
        assertThat(subject.getDelta(4).get().getAdditions()).containsExactly();
        assertThat(subject.getDelta(4).get().getRemovals()).containsExactly(4, 5, 6);
        assertThat(subject.getDelta(3).get().getAdditions()).containsExactly();
        assertThat(subject.getDelta(3).get().getRemovals()).containsExactly(3, 4, 5);
        assertThat(subject.getDelta(2).get().getAdditions()).containsExactly();
        assertThat(subject.getDelta(2).get().getRemovals()).containsExactly(2, 3, 4);
        assertThat(subject.getDelta(1).get().getAdditions()).containsExactly();
        assertThat(subject.getDelta(1).get().getRemovals()).containsExactly(1, 2, 3);
    }
}
