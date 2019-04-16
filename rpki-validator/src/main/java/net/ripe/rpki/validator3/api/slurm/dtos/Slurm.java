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
package net.ripe.rpki.validator3.api.slurm.dtos;

import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import net.ripe.ipresource.Asn;
import net.ripe.ipresource.IpRange;

import java.util.List;

@Data
public class Slurm {
    private final String slurmVersion = "1";

    private List<SlurmTarget> slurmTarget;

    private SlurmOutputFilters validationOutputFilters;

    private SlurmLocallyAddedAssertions locallyAddedAssertions;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class SlurmOutputFilters {
        private List<SlurmPrefixFilter> prefixFilters;
        private List<SlurmBgpSecFilter> bgpsecFilters;

    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class SlurmLocallyAddedAssertions {
        private List<SlurmPrefixAssertion> prefixAssertions;
        private List<SlurmBgpSecAssertion> bgpsecAssertions;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class SlurmBgpSecAssertion {
        private Asn asn;
        private String comment;
        @SerializedName("SKI")
        private String ski;
        private String publicKey;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class SlurmBgpSecFilter {
        private Asn asn;
        @SerializedName("SKI")
        private String ski;
        private String comment;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class SlurmPrefixAssertion {
        private Asn asn;
        private IpRange prefix;
        private Integer maxPrefixLength;
        private String comment;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class SlurmPrefixFilter {
        Asn asn;
        IpRange prefix;
        String comment;
    }

    @Data
    static class SlurmTarget {
        private Long asn;
        private String hostname;
    }
}
