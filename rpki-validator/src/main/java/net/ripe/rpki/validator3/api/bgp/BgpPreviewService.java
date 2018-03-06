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
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.ripe.ipresource.Asn;
import net.ripe.ipresource.IpRange;
import net.ripe.ipresource.etree.IntervalMap;
import net.ripe.ipresource.etree.IpResourceIntervalStrategy;
import net.ripe.ipresource.etree.NestedIntervalMap;
import net.ripe.rpki.validator3.api.Paging;
import net.ripe.rpki.validator3.api.SearchTerm;
import net.ripe.rpki.validator3.api.Sorting;
import net.ripe.rpki.validator3.domain.ValidatedRpkiObjects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@Slf4j
public class BgpPreviewService {

    @Autowired
    private BgpRisDownloader bgpRisDownloader;

    private List<BgpRisDump> bgpRisDumps = Arrays.asList(
        BgpRisDump.of(
            "http://www.ris.ripe.net/dumps/riswhoisdump.IPv4.gz",
            null,
            Collections.emptyList()
        ),
        BgpRisDump.of(
            "http://www.ris.ripe.net/dumps/riswhoisdump.IPv6.gz",
            null,
            Collections.emptyList()
        )
    );

    private ImmutableList<BgpRisEntry> bgpRisEntries = ImmutableList.of();

    private IntervalMap<IpRange, List<ValidatedRpkiObjects.RoaPrefix>> roaPrefixes = new NestedIntervalMap<>(IpResourceIntervalStrategy.getInstance());

    private ImmutableList<BgpPreviewEntry> bgpPreviewEntries = ImmutableList.of();

    public enum Validity {
        UNKNOWN, VALID, INVALID_ASN, INVALID_LENGTH
    }

    public List<ValidatingRoa> validity(Asn asn, IpRange prefix) {
        return null;
    }

    @Value(staticConstructor = "of")
    public static class BgpPreviewResult {
        int totalCount;
        Stream<BgpPreviewEntry> data;
    }

    @Value(staticConstructor = "of")
    public static class ValidatingRoa {
        Asn origin;
        IpRange prefix;
        String validity;
        String uri;
    }

    @Value(staticConstructor = "of")
    public static class BgpPreviewEntry {
        Asn origin;
        IpRange prefix;
        Validity validity;

        private static Predicate<BgpPreviewEntry> matches(SearchTerm searchTerm) {
            if (searchTerm == null) {
                return (x) -> true;
            }
            if (searchTerm.asAsn() != null) {
                Asn asn = searchTerm.asAsn();
                return (x) -> asn.equals(x.getOrigin());
            }
            if (searchTerm.asIpRange() != null) {
                IpRange range = searchTerm.asIpRange();
                return (x) -> range.overlaps(x.getPrefix());
            }
            switch (searchTerm.asString().trim().toUpperCase()) {
                case "VALID":
                    return (x) -> x.getValidity() == Validity.VALID;
                case "INVALID":
                    return (x) -> x.getValidity() == Validity.INVALID_ASN || x.getValidity() == Validity.INVALID_LENGTH;
                case "INVALID ASN":
                    return (x) -> x.getValidity() == Validity.INVALID_ASN;
                case "INVALID LENGTH":
                    return (x) -> x.getValidity() == Validity.INVALID_LENGTH;
                case "UNKNOWN":
                    return (x) -> x.getValidity() == Validity.UNKNOWN;
                default:
                    return (x) -> false;
            }
        }

        private static Comparator<? super BgpPreviewEntry> comparator(Sorting sorting) {
            Comparator<BgpPreviewEntry> columns;
            switch (sorting.getBy()) {
                case ASN:
                    columns = Comparator.comparing(BgpPreviewEntry::getOrigin)
                        .thenComparing(Comparator.comparing(BgpPreviewEntry::getPrefix))
                        .thenComparing(Comparator.comparing(BgpPreviewEntry::getValidity));
                    break;
                case VALIDITY:
                    columns = Comparator.comparing(BgpPreviewEntry::getValidity)
                        .thenComparing(Comparator.comparing(BgpPreviewEntry::getPrefix))
                        .thenComparing(Comparator.comparing(BgpPreviewEntry::getOrigin));
                    break;
                case TA:
                default:
                    columns = Comparator.comparing(BgpPreviewEntry::getPrefix)
                        .thenComparing(Comparator.comparing(BgpPreviewEntry::getOrigin))
                        .thenComparing(Comparator.comparing(BgpPreviewEntry::getValidity));
            }
            return sorting.getDirection() == Sorting.Direction.DESC ? columns.reversed() : columns;
        }
    }

    @Autowired
    public BgpPreviewService(ValidatedRpkiObjects validatedRpkiObjects) {
        validatedRpkiObjects.onUpdate(objects -> this.updateRoaPrefixes(objects.flatMap(x -> x.getRoaPrefixes().stream())));
    }

    public synchronized BgpPreviewResult find(SearchTerm searchTerm, Sorting sorting, Paging paging) {
        if (paging == null) {
            paging = Paging.of(0, Integer.MAX_VALUE);
        }
        if (sorting == null) {
            sorting = Sorting.of(Sorting.By.PREFIX, Sorting.Direction.ASC);
        }
        Predicate<BgpPreviewEntry> searchPredicate = BgpPreviewEntry.matches(searchTerm);

        int count = (int) bgpPreviewEntries
            .stream()
            .filter(searchPredicate)
            .count();

        Stream<BgpPreviewEntry> entries = bgpPreviewEntries
            .stream()
            .filter(searchPredicate)
            .sorted(BgpPreviewEntry.comparator(sorting))
            .skip(paging.getStartFrom())
            .limit(paging.getPageSize());
        return BgpPreviewResult.of(count, entries);
    }

    public synchronized void updateBgpRisDump(Collection<BgpRisDump> updated) {
        Instant start = Instant.now();
        try {
            ImmutableList.Builder<BgpRisEntry> bgpRisEntries = ImmutableList.builder();
            for (BgpRisDump dump : updated) {
                for (BgpRisEntry entry : dump.getEntries()) {
                    bgpRisEntries.add(entry);
                }
            }

            this.bgpRisEntries = bgpRisEntries.build();
            this.bgpPreviewEntries = validateBgpRisEntries(this.bgpRisEntries, this.roaPrefixes);
            this.bgpRisDumps = updated.stream().map(x -> BgpRisDump.of(x.getUrl(), x.getLastModified(), null)).collect(Collectors.toList());
        } finally {
            Instant stop = Instant.now();
            log.debug("BGP preview updated with {} BGP RIS entries in {} milliseconds", this.bgpRisEntries.size(), stop.toEpochMilli() - start.toEpochMilli());
        }
    }

    public synchronized void updateRoaPrefixes(Stream<ValidatedRpkiObjects.RoaPrefix> roaPrefixes) {
        Instant start = Instant.now();
        try {
            NestedIntervalMap<IpRange, List<ValidatedRpkiObjects.RoaPrefix>> prefixes = new NestedIntervalMap<>(IpResourceIntervalStrategy.getInstance());
            roaPrefixes.forEach(roaPrefix -> {
                List<ValidatedRpkiObjects.RoaPrefix> existing = prefixes.findExact(roaPrefix.getPrefix());
                if (existing == null) {
                    existing = new ArrayList<>(1);
                    prefixes.put(roaPrefix.getPrefix(), existing);
                }
                existing.add(roaPrefix);
            });

            this.roaPrefixes = prefixes;
            this.bgpPreviewEntries = validateBgpRisEntries(this.bgpRisEntries, this.roaPrefixes);
        } finally {
            Instant stop = Instant.now();
            log.debug("BGP preview updated with ROA prefixes in {} milliseconds", stop.toEpochMilli() - start.toEpochMilli());
        }
    }

    @Scheduled(initialDelay = 10_000L, fixedDelay = 600_0000L)
    private void downloadRisPreview() {
        updateBgpRisDump(bgpRisDumps.stream().map(bgpRisDownloader::fetch).collect(Collectors.toList()));
    }

    private ImmutableList<BgpPreviewEntry> validateBgpRisEntries(Iterable<BgpRisEntry> bgpRisEntries, IntervalMap<IpRange, List<ValidatedRpkiObjects.RoaPrefix>> roaPrefixes) {
        ImmutableList.Builder<BgpPreviewEntry> builder = ImmutableList.builder();
        for (BgpRisEntry bgpRisEntry : bgpRisEntries) {
            List<ValidatedRpkiObjects.RoaPrefix> matchingRoaPrefixes = roaPrefixes.findExactAndAllLessSpecific(bgpRisEntry.getPrefix()).stream().flatMap(x -> x.stream()).collect(Collectors.toList());
            List<ValidatedRpkiObjects.RoaPrefix> matchingAsnRoas = matchingRoaPrefixes.stream().filter(roaPrefix -> roaPrefix.getAsn().equals(bgpRisEntry.getOrigin())).collect(Collectors.toList());
            Validity validity;
            if (matchingRoaPrefixes.isEmpty()) {
                validity = Validity.UNKNOWN;
            } else if (matchingAsnRoas.isEmpty()) {
                validity = Validity.INVALID_ASN;
            } else if (!matchingAsnRoas.stream().anyMatch(roaPrefix -> roaPrefix.getEffectiveLength() >= bgpRisEntry.getPrefix().getPrefixLength())) {
                validity = Validity.INVALID_LENGTH;
            } else {
                validity = Validity.VALID;
            }

            builder.add(new BgpPreviewEntry(
                bgpRisEntry.getOrigin(),
                bgpRisEntry.getPrefix(),
                validity
            ));
        }
        return builder.build();
    }
}
