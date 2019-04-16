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
package net.ripe.rpki.validator3.api.bgp;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedSet;
import net.ripe.ipresource.Asn;
import net.ripe.ipresource.IpRange;
import net.ripe.rpki.validator3.api.ignorefilters.IgnoreFilterService;
import net.ripe.rpki.validator3.api.roaprefixassertions.RoaPrefixAssertionsService;
import net.ripe.rpki.validator3.api.slurm.SlurmStore;
import net.ripe.rpki.validator3.api.ignorefilters.IgnoreFilter;
import net.ripe.rpki.validator3.api.roaprefixassertions.RoaPrefixAssertion;
import net.ripe.rpki.validator3.domain.validation.ValidatedRpkiObjects;
import org.joda.time.DateTime;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Collection;
import java.util.Collections;
import java.util.stream.Stream;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

public class BgpPreviewServiceTest {

    private static final Asn AS_3333 = Asn.parse("AS3333");
    private static final Asn AS_2222 = Asn.parse("AS2222");

    private BgpPreviewService subject = createBgpPreviewService();

    @Test
    public void should_mark_non_matching_bgp_entry_as_unknown() {
        DateTime lastModified = DateTime.now();
        subject.updateBgpRisDump(ImmutableList.of(BgpRisDump.of("", lastModified, Optional.of(ImmutableList.of(BgpRisEntry.of(AS_3333, IpRange.parse("10.0.0.0/8"), 1000))))));
        subject.updateValidatedRoaPrefixes(ImmutableList.of(roa(AS_3333, "127.0.0.0/8", 8)).stream());

        BgpPreviewService.BgpPreviewResult bgpPreviewResult = subject.find(null, null, null);
        assertThat(bgpPreviewResult.getLastModified()).isEqualByComparingTo(lastModified.getMillis());
        assertThat(bgpPreviewResult.getData()).contains(
            BgpPreviewService.BgpPreviewEntry.of(AS_3333, IpRange.parse("10.0.0.0/8"), BgpPreviewService.Validity.UNKNOWN)
        );
    }

    @Test
    public void should_validate_matching_bgp_entry() {
        subject.updateBgpRisDump(ImmutableList.of(BgpRisDump.of("", null, Optional.of(ImmutableList.of(BgpRisEntry.of(AS_3333, IpRange.parse("10.0.0.0/8"), 1000))))));
        subject.updateValidatedRoaPrefixes(ImmutableList.of(roa(AS_3333, "0.0.0.0/4", 8)).stream());

        assertThat(subject.find(null, null, null).getData()).contains(
            BgpPreviewService.BgpPreviewEntry.of(AS_3333, IpRange.parse("10.0.0.0/8"), BgpPreviewService.Validity.VALID)
        );
    }

    @Test
    public void should_reject_too_specific_bgp_entry() {
        subject.updateBgpRisDump(ImmutableList.of(BgpRisDump.of("", null, Optional.of(ImmutableList.of(BgpRisEntry.of(AS_3333, IpRange.parse("10.0.0.0/8"), 1000))))));
        subject.updateValidatedRoaPrefixes(ImmutableList.of(roa(AS_3333, "0.0.0.0/4", null)).stream());

        assertThat(subject.find(null, null, null).getData()).contains(
            BgpPreviewService.BgpPreviewEntry.of(AS_3333, IpRange.parse("10.0.0.0/8"), BgpPreviewService.Validity.INVALID_LENGTH)
        );
    }

    @Test
    public void should_reject_bgp_entry_for_different_asn() {
        subject.updateBgpRisDump(ImmutableList.of(BgpRisDump.of("", null, Optional.of(ImmutableList.of(BgpRisEntry.of(AS_3333, IpRange.parse("10.0.0.0/8"), 1000))))));
        subject.updateValidatedRoaPrefixes(ImmutableList.of(roa(Asn.parse("AS3"), "10.0.0.0/8", null)).stream());

        assertThat(subject.find(null, null, null).getData()).contains(
            BgpPreviewService.BgpPreviewEntry.of(AS_3333, IpRange.parse("10.0.0.0/8"), BgpPreviewService.Validity.INVALID_ASN)
        );
    }

    @Test
    public void should_reject_bgp_entry_when_roa_exists_but_ignored_by_asn_based_filter() {
        subject = createBgpPreviewService(ImmutableList.of(ignoreFilter(AS_3333.longValue(), null)));
        subject.updateBgpRisDump(ImmutableList.of(BgpRisDump.of("", null, Optional.of(ImmutableList.of(BgpRisEntry.of(AS_3333, IpRange.parse("10.0.0.0/8"), 1000))))));
        subject.updateValidatedRoaPrefixes(ImmutableList.of(roa(AS_3333, "10.0.0.0/8", 8)).stream());

        assertThat(subject.find(null, null, null).getData()).contains(
                BgpPreviewService.BgpPreviewEntry.of(AS_3333, IpRange.parse("10.0.0.0/8"), BgpPreviewService.Validity.UNKNOWN)
        );
    }

    @Test
    public void should_reject_bgp_entry_when_roa_exists_but_ignored_by_prefix_based_filter() {
        subject = createBgpPreviewService(ImmutableList.of(ignoreFilter(null, "10.0.0.0/8")));
        subject.updateBgpRisDump(ImmutableList.of(BgpRisDump.of("", null, Optional.of(ImmutableList.of(BgpRisEntry.of(AS_3333, IpRange.parse("10.0.0.0/8"), 1000))))));
        subject.updateValidatedRoaPrefixes(ImmutableList.of(roa(AS_3333, "10.0.0.0/8", 8)).stream());

        assertThat(subject.find(null, null, null).getData()).contains(
                BgpPreviewService.BgpPreviewEntry.of(AS_3333, IpRange.parse("10.0.0.0/8"), BgpPreviewService.Validity.UNKNOWN)
        );
    }

    @Test
    public void should_reject_bgp_entry_when_roa_exists_but_ignored_by_filter_with_covering_prefix() {
        subject = createBgpPreviewService(ImmutableList.of(ignoreFilter(null, "0.0.0.0/4")));
        subject.updateBgpRisDump(ImmutableList.of(BgpRisDump.of("", null, Optional.of(ImmutableList.of(BgpRisEntry.of(AS_3333, IpRange.parse("10.0.0.0/8"), 1000))))));
        subject.updateValidatedRoaPrefixes(ImmutableList.of(roa(AS_3333, "10.0.0.0/8", 8)).stream());

        assertThat(subject.find(null, null, null).getData()).contains(
                BgpPreviewService.BgpPreviewEntry.of(AS_3333, IpRange.parse("10.0.0.0/8"), BgpPreviewService.Validity.UNKNOWN)
        );
    }

    @Test
    public void should_reject_bgp_entry_when_roa_exists_but_ignored_by_ans_and_prefix_based_filter() {
        subject = createBgpPreviewService(ImmutableList.of(ignoreFilter(AS_3333.longValue(), "10.0.0.0/8")));
        subject.updateBgpRisDump(ImmutableList.of(BgpRisDump.of("", null, Optional.of(ImmutableList.of(BgpRisEntry.of(AS_3333, IpRange.parse("10.0.0.0/8"), 1000))))));
        subject.updateValidatedRoaPrefixes(ImmutableList.of(roa(AS_3333, "10.0.0.0/8", 8)).stream());

        assertThat(subject.find(null, null, null).getData()).contains(
                BgpPreviewService.BgpPreviewEntry.of(AS_3333, IpRange.parse("10.0.0.0/8"), BgpPreviewService.Validity.UNKNOWN)
        );
    }

    @Test
    public void should_accept_bgp_entry_when_ignore_filter_is_not_exactly_matching_because_asn_differs() {
        subject = createBgpPreviewService(ImmutableList.of(ignoreFilter(AS_2222.longValue(), "10.0.0.0/8")));
        subject.updateBgpRisDump(ImmutableList.of(BgpRisDump.of("", null, Optional.of(ImmutableList.of(BgpRisEntry.of(AS_3333, IpRange.parse("10.0.0.0/8"), 1000))))));
        subject.updateValidatedRoaPrefixes(ImmutableList.of(roa(AS_3333, "10.0.0.0/8", 8)).stream());

        assertThat(subject.find(null, null, null).getData()).contains(
                BgpPreviewService.BgpPreviewEntry.of(AS_3333, IpRange.parse("10.0.0.0/8"), BgpPreviewService.Validity.VALID)
        );
    }

    @Test
    public void should_accept_bgp_entry_when_ignore_filter_is_not_exactly_matching_because_prefix_is_too_small() {
        subject = createBgpPreviewService(ImmutableList.of(ignoreFilter(AS_3333.longValue(), "10.10.0.0/16")));
        subject.updateBgpRisDump(ImmutableList.of(BgpRisDump.of("", null, Optional.of(ImmutableList.of(BgpRisEntry.of(AS_3333, IpRange.parse("10.0.0.0/8"), 1000))))));
        subject.updateValidatedRoaPrefixes(ImmutableList.of(roa(AS_3333, "10.0.0.0/8", 8)).stream());

        assertThat(subject.find(null, null, null).getData()).contains(
                BgpPreviewService.BgpPreviewEntry.of(AS_3333, IpRange.parse("10.0.0.0/8"), BgpPreviewService.Validity.VALID)
        );
    }

    private ValidatedRpkiObjects.RoaPrefix roa(Asn asn, String prefix, Integer maximumLength) {
        return ValidatedRpkiObjects.RoaPrefix.of(null, asn, IpRange.parse(prefix), maximumLength, maximumLength != null ? maximumLength : IpRange.parse(prefix).getPrefixLength(), ImmutableSortedSet.of());
    }

    private BgpPreviewService createBgpPreviewService() {
        return createBgpPreviewService(Collections.emptyList());
    }

    private BgpPreviewService createBgpPreviewService(final Collection<IgnoreFilter> ignoreFilters) {
        return new BgpPreviewService(new String[0],5, null, new ValidatedRpkiObjects(), new IgnoreFilterService(createSlurmStore()) {
            @Override
            public Stream<IgnoreFilter> all() {
                return ignoreFilters.stream();
            }
        }, new RoaPrefixAssertionsService(slurmStore) {
            @Override
            public Stream<RoaPrefixAssertion> all() {
                return Stream.empty();
            }
        });
    }

    private SlurmStore createSlurmStore()  {
        try {
            return new SlurmStore(Files.createTempDirectory("slurm-").toFile().getAbsolutePath());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private IgnoreFilter ignoreFilter(Long asn, String prefix) {
        IgnoreFilter f = new IgnoreFilter();
        f.setAsn(asn);
        f.setPrefix(prefix);
        return f;
    }
}
