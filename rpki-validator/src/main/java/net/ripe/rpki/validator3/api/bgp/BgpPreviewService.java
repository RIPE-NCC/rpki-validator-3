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
import lombok.extern.slf4j.Slf4j;
import net.ripe.ipresource.Asn;
import net.ripe.ipresource.IpRange;
import net.ripe.ipresource.etree.IntervalMap;
import net.ripe.ipresource.etree.IpResourceIntervalStrategy;
import net.ripe.ipresource.etree.NestedIntervalMap;
import net.ripe.rpki.validator3.api.Paging;
import net.ripe.rpki.validator3.api.SearchTerm;
import net.ripe.rpki.validator3.api.Sorting;
import net.ripe.rpki.validator3.api.ignorefilters.IgnoreFilterService;
import net.ripe.rpki.validator3.api.roaprefixassertions.RoaPrefixAssertionsService;
import net.ripe.rpki.validator3.domain.IgnoreFilter;
import net.ripe.rpki.validator3.domain.IgnoreFiltersPredicate;
import net.ripe.rpki.validator3.domain.RoaPrefixAssertion;
import net.ripe.rpki.validator3.domain.RoaPrefixDefinition;
import net.ripe.rpki.validator3.domain.ValidatedRpkiObjects;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@Slf4j
public class BgpPreviewService {

    private final int bgpRisVisibilityThreshold;

    private final BgpRisDownloader bgpRisDownloader;

    private List<BgpRisDump> bgpRisDumps;

    private IntervalMap<IpRange, List<RoaPrefix>> roaPrefixes = new NestedIntervalMap<>(IpResourceIntervalStrategy.getInstance());
    private IntervalMap<IpRange, List<RoaPrefix>> filteredRoaPrefixes = new NestedIntervalMap<>(IpResourceIntervalStrategy.getInstance());

    private ImmutableList<RoaPrefix> validatedRoaPrefixes = ImmutableList.of();
    private ImmutableList<RoaPrefix> roaPrefixAssertions = ImmutableList.of();
    private ImmutableList<IgnoreFilter> ignoreFilters = ImmutableList.of();
    private Map<String, ImmutableList<BgpPreviewEntry>> bgpPreviewEntries = new TreeMap<>();

    public enum Validity {
        UNKNOWN, VALID, INVALID_ASN, INVALID_LENGTH
    }

    @lombok.Value(staticConstructor = "of")
    public static class BgpPreviewResult {
        int totalCount;
        Stream<BgpPreviewEntry> data;
    }

    @lombok.Value(staticConstructor = "of")
    public static class ValidatingRoa {
        String origin;
        String prefix;
        String validity;
        Integer maxLength;
        String source;
        String uri;
        Long roaPrefixAssertionId;
        String roaPrefixAssertionComment;
    }

    @lombok.Value(staticConstructor = "of")
    public static class BgpValidity {
        String origin;
        String prefix;
        String validity;
        List<ValidatingRoa> validatingRoas;
    }

    @lombok.Value(staticConstructor = "of")
    public static class BgpValidityWithFilteredResource {
        String origin;
        String prefix;
        String validity;
        List<ValidatingRoa> validatingRoas;
        List<ValidatingRoa> filteredRoas;
    }

    @lombok.Value(staticConstructor = "of")
    private static class RoaPrefix implements RoaPrefixDefinition {
        ValidatedRpkiObjects.TrustAnchorData trustAnchor;
        ImmutableSortedSet<String> locations;
        Long roaPrefixAssertionId;
        String comment;

        Asn asn;
        IpRange prefix;
        Integer maximumLength;
        int effectiveLength;
    }

    @lombok.Value(staticConstructor = "of")
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
                case "ASN":
                case "INVALID ASN":
                    return (x) -> x.getValidity() == Validity.INVALID_ASN;
                case "LENGTH":
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
    public BgpPreviewService(
        @Value("${rpki.validator.bgp.ris.dump.urls}") String[] bgpRisDumpUrls,
        @Value("${rpki.validator.bgp.ris.visibility.threshold}") int bgpRisVisibilityThreshold,
        BgpRisDownloader bgpRisDownloader,
        ValidatedRpkiObjects validatedRpkiObjects,
        IgnoreFilterService ignoreFilterService,
        RoaPrefixAssertionsService roaPrefixAssertionsService
    ) {
        this.bgpRisVisibilityThreshold = bgpRisVisibilityThreshold;
        this.bgpRisDumps = Arrays.stream(bgpRisDumpUrls).map(url -> BgpRisDump.of(
            url,
            null,
            Optional.empty()
        )).collect(Collectors.toList());
        this.bgpRisDownloader = bgpRisDownloader;

        validatedRpkiObjects.addListener(objects -> updateValidatedRoaPrefixes(objects.stream().flatMap(x -> x.getRoaPrefixes().stream())));
        ignoreFilterService.addListener(filters -> updateIgnoreFilters(filters));
        roaPrefixAssertionsService.addListener(roaPrefixAssertions -> updateRoaPrefixAssertions(roaPrefixAssertions));
    }

    @Scheduled(initialDelay = 10_000L, fixedDelay = 600_000L)
    private void downloadRisPreview() {
        updateBgpRisDump(bgpRisDumps.stream().map(bgpRisDownloader::fetch).collect(Collectors.toList()));
    }

    public synchronized BgpPreviewResult find(SearchTerm searchTerm, Sorting sorting, Paging paging) {
        if (paging == null) {
            paging = Paging.of(0, Integer.MAX_VALUE);
        }
        if (sorting == null) {
            sorting = Sorting.of(Sorting.By.PREFIX, Sorting.Direction.ASC);
        }
        final Predicate<BgpPreviewEntry> searchPredicate = BgpPreviewEntry.matches(searchTerm);

        int count = (int) bgpPreviewEntries
            .values()
            .stream()
            .flatMap(Collection::stream)
            .filter(searchPredicate)
            .count();

        Stream<BgpPreviewEntry> entries = bgpPreviewEntries
            .values()
            .stream()
            .flatMap(Collection::stream)
            .filter(searchPredicate)
            .sorted(BgpPreviewEntry.comparator(sorting))
            .skip(paging.getStartFrom())
            .limit(paging.getPageSize());

        return BgpPreviewResult.of(count, entries);
    }

    public synchronized List<BgpPreviewEntry> findAffected(Asn asn, IpRange prefix, Integer maximumLength) {
        return bgpPreviewEntries
            .values()
            .parallelStream()
            .flatMap(Collection::stream)
            .filter(entry -> prefix.contains(entry.getPrefix()))
            .collect(Collectors.toList());
    }

    public synchronized void updateBgpRisDump(Collection<BgpRisDump> updated) {
        Map<String, ImmutableList<BgpPreviewEntry>> updatedDumps = new HashMap<>(this.bgpPreviewEntries);
        for (BgpRisDump dump : updated) {
            dump.getEntries().ifPresent(entries -> {
                    ImmutableList.Builder<BgpPreviewEntry> bgpRisEntries = ImmutableList.builder();
                    for (BgpRisEntry entry : entries) {
                        if (entry.getVisibility() >= bgpRisVisibilityThreshold) {
                            bgpRisEntries.add(BgpPreviewEntry.of(
                                entry.getOrigin(),
                                entry.getPrefix(),
                                Validity.UNKNOWN
                            ));
                        }
                    }
                    updatedDumps.put(dump.getUrl(), bgpRisEntries.build());
                });
        }

        this.bgpPreviewEntries = validateBgpRisEntries(updatedDumps, this.roaPrefixes);

        this.bgpRisDumps = updated.stream().map(x -> BgpRisDump.of(x.getUrl(), x.getLastModified(), Optional.empty())).collect(Collectors.toList());
    }

    public synchronized void updateValidatedRoaPrefixes(Stream<ValidatedRpkiObjects.RoaPrefix> prefixes) {
        this.validatedRoaPrefixes = ImmutableList.copyOf(prefixes
            .map(p -> RoaPrefix.of(
                p.getTrustAnchor(),
                p.getLocations(),
                null,
                null,
                p.getAsn(),
                p.getPrefix(),
                p.getMaximumLength(),
                p.getEffectiveLength()
            ))
            .collect(Collectors.toList())
        );

        this.roaPrefixes = recalculateRoaPrefixes(this.validatedRoaPrefixes, this.ignoreFilters, this.roaPrefixAssertions);
        this.bgpPreviewEntries = validateBgpRisEntries(this.bgpPreviewEntries, this.roaPrefixes);
    }

    private synchronized void updateIgnoreFilters(Collection<IgnoreFilter> filters) {
        this.ignoreFilters = ImmutableList.copyOf(filters);

        this.roaPrefixes = recalculateRoaPrefixes(this.validatedRoaPrefixes, this.ignoreFilters, this.roaPrefixAssertions);
        this.filteredRoaPrefixes = recalculateFilteredRoaPrefixes(this.validatedRoaPrefixes, this.ignoreFilters);
        this.bgpPreviewEntries = validateBgpRisEntries(this.bgpPreviewEntries, this.roaPrefixes);
    }

    private synchronized void updateRoaPrefixAssertions(Collection<RoaPrefixAssertion> assertions) {
        this.roaPrefixAssertions = ImmutableList.copyOf(
            assertions
                .stream()
                .map(p -> RoaPrefix.of(
                    null,
                    null,
                    p.getId(),
                    p.getComment(),
                    new Asn(p.getAsn()),
                    IpRange.parse(p.getPrefix()),
                    p.getMaximumLength(),
                    p.getMaximumLength() == null ? IpRange.parse(p.getPrefix()).getPrefixLength() : p.getMaximumLength()
                ))
                .collect(Collectors.toList())
        );

        this.roaPrefixes = recalculateRoaPrefixes(this.validatedRoaPrefixes, this.ignoreFilters, this.roaPrefixAssertions);
        this.bgpPreviewEntries = validateBgpRisEntries(this.bgpPreviewEntries, this.roaPrefixes);
    }

    private static NestedIntervalMap<IpRange, List<RoaPrefix>> recalculateRoaPrefixes(
        ImmutableList<RoaPrefix> validatedRoaPrefixes,
        ImmutableList<IgnoreFilter> ignoreFilters,
        ImmutableList<RoaPrefix> roaPrefixAssertions
    ) {
        NestedIntervalMap<IpRange, List<RoaPrefix>> roaPrefixes = new NestedIntervalMap<>(IpResourceIntervalStrategy.getInstance());
        Stream.concat(
            validatedRoaPrefixes
                .stream()
                .filter(new IgnoreFiltersPredicate(ignoreFilters.stream()).negate()),
            roaPrefixAssertions.stream()
        ).forEach(p -> {
            List<RoaPrefix> existing = roaPrefixes.findExact(p.getPrefix());
            if (existing == null) {
                existing = new ArrayList<>(1);
                roaPrefixes.put(p.getPrefix(), existing);
            }
            existing.add(p);
        });
        return roaPrefixes;
    }

    private static NestedIntervalMap<IpRange, List<RoaPrefix>> recalculateFilteredRoaPrefixes(
            ImmutableList<RoaPrefix> validatedRoaPrefixes,
            ImmutableList<IgnoreFilter> ignoreFilters
    ) {
        NestedIntervalMap<IpRange, List<RoaPrefix>> roaPrefixes = new NestedIntervalMap<>(IpResourceIntervalStrategy.getInstance());
        validatedRoaPrefixes
                .stream()
                .filter(new IgnoreFiltersPredicate(ignoreFilters.stream()))
                .forEach(p -> {
                    List<RoaPrefix> existing = roaPrefixes.findExact(p.getPrefix());
                    if (existing == null) {
                        existing = new ArrayList<>(1);
                        roaPrefixes.put(p.getPrefix(), existing);
                    }
                    existing.add(p);
                });
        return roaPrefixes;
    }

    private <T> Map<T, ImmutableList<BgpPreviewEntry>> validateBgpRisEntries(Map<T, ImmutableList<BgpPreviewEntry>> bgpRisEntries, IntervalMap<IpRange, List<RoaPrefix>> roaPrefixes) {
        return bgpRisEntries.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, entry -> validateBgpRisEntries(entry.getValue(), roaPrefixes)));
    }

    private ImmutableList<BgpPreviewEntry> validateBgpRisEntries(
        ImmutableList<BgpPreviewEntry> bgpRisEntries,
        IntervalMap<IpRange, List<RoaPrefix>> roaPrefixes
    ) {
        long begin = System.currentTimeMillis();

        final ImmutableList.Builder<BgpPreviewEntry> builder = ImmutableList.builder();
        bgpRisEntries.parallelStream().map(bgpRisEntry -> {
            final Validity validity = validateBgpRisEntry(roaPrefixes, bgpRisEntry);
            return new BgpPreviewEntry(
                    bgpRisEntry.getOrigin(),
                    bgpRisEntry.getPrefix(),
                    validity
            );
        }).forEachOrdered(e -> builder.add(e));
        ImmutableList<BgpPreviewEntry> built = builder.build();

        long end = System.currentTimeMillis();
        log.debug(
            "validateBgpRisEntries duration: {} ms ({} RIS entries, {} validated ROA prefixes, {} ignore filters, {} ROA prefix assertions)",
            end - begin,
            bgpRisEntries.size(),
            validatedRoaPrefixes.size(),
            ignoreFilters.size(),
            roaPrefixAssertions.size()
        );
        return built;
    }

    private static Validity validateBgpRisEntry(
        IntervalMap<IpRange, List<RoaPrefix>> roaPrefixes,
        BgpPreviewEntry bgpRisEntry
    ) {
        List<RoaPrefix> matchingRoaPrefixes = roaPrefixes
                .findExactAndAllLessSpecific(bgpRisEntry.getPrefix())
                .stream()
                .flatMap(Collection::stream)
                .collect(Collectors.toList());

        return validateBgpRisEntry(matchingRoaPrefixes, bgpRisEntry);
    }

    private static Validity validateBgpRisEntry(List<RoaPrefix> matchingRoaPrefixes, BgpPreviewEntry bgpRisEntry) {
        List<RoaPrefix> matchingAsnRoas = matchingRoaPrefixes
                .stream()
                .filter(roaPrefix -> roaPrefix.getAsn().equals(bgpRisEntry.getOrigin()))
                .collect(Collectors.toList());

        final Validity validity;
        if (matchingRoaPrefixes.isEmpty()) {
            validity = Validity.UNKNOWN;
        } else if (matchingAsnRoas.isEmpty()) {
            validity = Validity.INVALID_ASN;
        } else if (matchingAsnRoas.stream().noneMatch(roaPrefix -> roaPrefix.getEffectiveLength() >= bgpRisEntry.getPrefix().getPrefixLength())) {
            validity = Validity.INVALID_LENGTH;
        } else {
            validity = Validity.VALID;
        }
        return validity;
    }

    public BgpValidityWithFilteredResource validity(final Asn origin, final IpRange prefix) {
        BgpValidity roas = validity(origin, prefix, this.roaPrefixes);
        BgpValidity filtered = validity(origin, prefix, this.filteredRoaPrefixes);
        List<ValidatingRoa> filteredRoasWithoutUnknown = filtered.getValidatingRoas()
                .stream()
                .filter(r -> !Validity.UNKNOWN.toString().equals(r.getValidity()))
                .collect(Collectors.toList());

        return BgpValidityWithFilteredResource.of(
                origin.toString(),
                prefix.toString(),
                roas.getValidity(),
                roas.getValidatingRoas(),
                filteredRoasWithoutUnknown);
    }

    private BgpValidity validity(final Asn origin, final IpRange prefix, final IntervalMap<IpRange, List<RoaPrefix>> prefixes) {
        final List<Pair<RoaPrefix, Validity>> matchingRoaPrefixes = prefixes.findExactAndAllLessSpecific(prefix)
                .stream()
                .flatMap(x -> x.stream())
                .map(r -> {
                    final BgpPreviewEntry bgpPreviewEntry = BgpPreviewEntry.of(origin, prefix, Validity.UNKNOWN);
                    final Validity validity = validateBgpRisEntry(Collections.singletonList(r), bgpPreviewEntry);
                    return Pair.of(r, validity);
                })
                .sorted(Comparator.comparingInt(p -> {
                    switch (p.getRight()) {
                        case VALID:
                            return 0;
                        case INVALID_LENGTH:
                            return 1;
                        case INVALID_ASN:
                            return 2;
                    }
                    return 10;
                }))
                .collect(Collectors.toList());

        final Validity validity = matchingRoaPrefixes.stream().findFirst().map(p -> p.getRight()).orElse(Validity.UNKNOWN);

        final List<ValidatingRoa> validatingRoaStream = matchingRoaPrefixes
                .stream()
                .flatMap(p -> {
                    final RoaPrefix r = p.getLeft();
                    if (r.getTrustAnchor() != null) {
                        return r.getLocations().stream().map(loc -> ValidatingRoa.of(
                            r.getAsn().toString(),
                            r.getPrefix().toString(),
                            p.getRight().toString(),
                            r.getMaximumLength(),
                            r.getTrustAnchor() == null ? null : r.getTrustAnchor().getName(),
                            loc,
                            null,
                            null));
                    } else if (r.getRoaPrefixAssertionId() != null) {
                        return Stream.of(ValidatingRoa.of(
                           r.getAsn().toString(),
                           r.getPrefix().toString(),
                           p.getRight().toString(),
                           r.getMaximumLength(),
                           "Whitelist",
                           null,
                            r.getRoaPrefixAssertionId(),
                            r.getComment()
                        ));
                    } else {
                        return Stream.empty();
                    }
                })
                .distinct()
                .collect(Collectors.toList());

        return BgpValidity.of(origin.toString(), prefix.toString(), validity.toString(), validatingRoaStream);
    }
}
