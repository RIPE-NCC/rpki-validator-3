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
import net.ripe.rpki.validator3.api.ignorefilters.IgnoreFilter;
import net.ripe.rpki.validator3.api.ignorefilters.IgnoreFilterService;
import net.ripe.rpki.validator3.api.roaprefixassertions.RoaPrefixAssertion;
import net.ripe.rpki.validator3.api.roaprefixassertions.RoaPrefixAssertionsService;
import net.ripe.rpki.validator3.domain.IgnoreFiltersPredicate;
import net.ripe.rpki.validator3.domain.RoaPrefixDefinition;
import net.ripe.rpki.validator3.domain.validation.ValidatedRpkiObjects;
import net.ripe.rpki.validator3.util.Time;
import org.apache.commons.lang3.tuple.Pair;
import org.joda.time.DateTime;
import javax.inject.Inject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.AbstractCollection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Comparator.comparing;
import static java.util.Comparator.comparingInt;

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
        long lastModified;
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
        PackedIpRange prefix;
        Integer maximumLength;
        int effectiveLength;

        public IpRange getPrefix() {
            return prefix.toIpRange();
        }
    }

    @lombok.Value(staticConstructor = "of")
    public static class BgpPreviewEntry {
        // store it in memory optimised way, as we are going to store a lot of these objects in memory
        long origin;
        PackedIpRange prefix;
        Validity validity;

        BgpPreviewEntry(Asn origin, IpRange prefix, Validity validity) {
            this.origin = origin.longValue();
            this.prefix = new PackedIpRange(prefix);
            this.validity = validity;
        }

        BgpPreviewEntry(long origin, PackedIpRange prefix, Validity validity) {
            this.origin = origin;
            this.prefix = prefix;
            this.validity = validity;
        }

        public static BgpPreviewEntry of(Asn origin, IpRange prefix, Validity validity) {
            return new BgpPreviewEntry(origin, prefix, validity);
        }

        private static Predicate<BgpPreviewEntry> matches(SearchTerm searchTerm) {
            if (searchTerm == null) {
                return x -> true;
            }
            if (searchTerm.asAsn() != null) {
                Asn asn = searchTerm.asAsn();
                return x -> asn.longValue() == x.origin;
            }
            if (searchTerm.asIpRange() != null) {
                IpRange range = searchTerm.asIpRange();
                return x -> range.overlaps(x.getPrefix());
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
                    columns = comparing(BgpPreviewEntry::getOrigin)
                            .thenComparing(BgpPreviewEntry::getPrefix)
                            .thenComparing(BgpPreviewEntry::getValidity);
                    break;
                case VALIDITY:
                    columns = comparing(BgpPreviewEntry::getValidity)
                            .thenComparing(BgpPreviewEntry::getPrefix)
                            .thenComparing(BgpPreviewEntry::getOrigin);
                    break;
                case TA:
                default:
                    columns = comparing(BgpPreviewEntry::getPrefix)
                            .thenComparing(BgpPreviewEntry::getOrigin)
                            .thenComparing(BgpPreviewEntry::getValidity);
            }
            return sorting.getDirection() == Sorting.Direction.DESC ? columns.reversed() : columns;
        }

        BgpPreviewEntry ofValidity(Validity validity) {
            return new BgpPreviewEntry(origin, prefix, validity);
        }

        public Asn getOrigin() {
            return new Asn(origin);
        }

        public IpRange getPrefix() {
            return prefix.toIpRange();
        }
    }

    @Inject
    public BgpPreviewService(
            @Value("${rpki.validator.bgp.ris.dump.urls}") String[] bgpRisDumpUrls,
            @Value("${rpki.validator.bgp.ris.visibility.threshold}") int bgpRisVisibilityThreshold,
            BgpRisDownloader bgpRisDownloader,
            ValidatedRpkiObjects validatedRpkiObjects,
            IgnoreFilterService ignoreFilterService,
            RoaPrefixAssertionsService roaPrefixAssertionsService
    ) {
        this.bgpRisVisibilityThreshold = bgpRisVisibilityThreshold;
        this.bgpRisDownloader = bgpRisDownloader;
        writeLocked(() -> this.bgpRisDumps = Arrays.stream(bgpRisDumpUrls).map(url ->
                BgpRisDump.of(
                        url,
                        null,
                        Optional.empty()
                )).collect(Collectors.toList())
        );

        validatedRpkiObjects.addListener(objects -> updateValidatedRoaPrefixes(objects.stream().flatMap(x -> x.getRoaPrefixes().stream())));
        ignoreFilterService.addListener(this::updateIgnoreFilters);
        roaPrefixAssertionsService.addListener(this::updateRoaPrefixAssertions);
    }

    private ReentrantReadWriteLock dataLock = new ReentrantReadWriteLock();
    private ReentrantLock modificationLock = new ReentrantLock();

    private void sequential(Runnable r) {
        locked(modificationLock, r);
    }

    private void writeLocked(Runnable s) {
        locked(dataLock.writeLock(), s);
    }

    private <T> T readLocked(Supplier<T> s) {
        AtomicReference<T> r = new AtomicReference<>();
        locked(dataLock.readLock(), () -> r.set(s.get()));
        return r.get();
    }

    private static void locked(Lock lock, Runnable s) {
        lock.lock();
        try {
            s.run();
        } finally {
            lock.unlock();
        }
    }

    public void downloadRisPreview() {
        sequential(() -> {
            log.info("Updating BGP RIS dumps");
            final List<BgpRisDump> updated = bgpRisDumps.stream()
                    .map(bgpRisDownloader::fetch)
                    .collect(Collectors.toList());
            updateBgpRisDump(updated);
            log.info("Finished updating BGP RIS dumps");
        });
    }

    public BgpPreviewResult find(SearchTerm searchTerm, Sorting sorting, Paging paging) {
        if (paging == null) {
            paging = Paging.of(0L, Long.MAX_VALUE);
        }
        if (sorting == null) {
            sorting = Sorting.of(Sorting.By.PREFIX, Sorting.Direction.ASC);
        }
        final Sorting finalSorting = sorting;
        final Paging finalPaging = paging;
        final Predicate<BgpPreviewEntry> searchPredicate = BgpPreviewEntry.matches(searchTerm);

        return readLocked(() -> {
            final int count;
            if (searchTerm == null) {
                count = bgpPreviewEntries.values().stream().map(AbstractCollection::size).reduce(0, Integer::sum);
             } else {
                count = (int) bgpPreviewEntries
                        .values()
                        .stream()
                        .flatMap(Collection::stream)
                        .filter(searchPredicate)
                        .count();
            }

            Stream<BgpPreviewEntry> entries = bgpPreviewEntries
                    .values()
                    .stream()
                    .flatMap(Collection::stream)
                    .filter(searchPredicate)
                    .sorted(BgpPreviewEntry.comparator(finalSorting))
                    .skip(finalPaging.getStartFrom())
                    .limit(finalPaging.getPageSize());

            DateTime lastModified = bgpRisDumps.stream()
                    .map(BgpRisDump::getLastModified)
                    .filter(Objects::nonNull)
                    .max(Comparator.naturalOrder())
                    .orElse(DateTime.now());

            return BgpPreviewResult.of(count, lastModified.getMillis(), entries);
        });
    }

    public List<BgpPreviewEntry> findAffected(Asn asn, IpRange prefix, Integer maximumLength) {
        return readLocked(() -> bgpPreviewEntries
                .values()
                .parallelStream()
                .flatMap(Collection::stream)
                .filter(entry -> prefix.contains(entry.getPrefix()))
                .collect(Collectors.toList()));
    }

    public void updateBgpRisDump(Collection<BgpRisDump> updated) {
        sequential(() -> {
            final Map<String, ImmutableList<BgpPreviewEntry>> updatedDumps = new HashMap<>(this.bgpPreviewEntries);
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

            final Map<String, ImmutableList<BgpPreviewEntry>> updatedBgpPreviewEntries = validateBgpRisEntries(updatedDumps, this.roaPrefixes);
            final List<BgpRisDump> updatedBgpDumps = updated.stream().map(x -> BgpRisDump.of(x.getUrl(), x.getLastModified(), Optional.empty())).collect(Collectors.toList());
            writeLocked(() -> {
                this.bgpPreviewEntries = updatedBgpPreviewEntries;
                this.bgpRisDumps = updatedBgpDumps;
            });
        });
    }

    void updateValidatedRoaPrefixes(Stream<ValidatedRpkiObjects.RoaPrefix> prefixes) {
        sequential(() -> {
            final ImmutableList<RoaPrefix> validatedRoaPrefixes = ImmutableList.copyOf(prefixes
                    .map(p -> RoaPrefix.of(
                            p.getTrustAnchor(),
                            p.getLocations(),
                            null,
                            null,
                            p.getAsn(),
                            new PackedIpRange(p.getPrefix()),
                            p.getMaximumLength(),
                            p.getEffectiveLength()
                    ))
                    .iterator()
            );
            final NestedIntervalMap<IpRange, List<RoaPrefix>> roaPrefixes = recalculateRoaPrefixes(validatedRoaPrefixes, this.ignoreFilters, this.roaPrefixAssertions);
            final NestedIntervalMap<IpRange, List<RoaPrefix>> filteredRoaPrefixes = recalculateFilteredRoaPrefixes(validatedRoaPrefixes, this.ignoreFilters);
            final Map<String, ImmutableList<BgpPreviewEntry>> validatedBgpPreviewEntries = validateBgpRisEntries(this.bgpPreviewEntries, roaPrefixes);

            writeLocked(() -> {
                this.validatedRoaPrefixes = validatedRoaPrefixes;
                this.roaPrefixes = roaPrefixes;
                this.filteredRoaPrefixes = filteredRoaPrefixes;
                this.bgpPreviewEntries = validatedBgpPreviewEntries;
            });
        });
    }

    private void updateIgnoreFilters(Collection<IgnoreFilter> filters) {
        sequential(() -> {
            final NestedIntervalMap<IpRange, List<RoaPrefix>> roaPrefixes = recalculateRoaPrefixes(this.validatedRoaPrefixes, this.ignoreFilters, this.roaPrefixAssertions);
            final NestedIntervalMap<IpRange, List<RoaPrefix>> filteredRoaPrefixes = recalculateFilteredRoaPrefixes(this.validatedRoaPrefixes, this.ignoreFilters);

            writeLocked(() -> {
                this.ignoreFilters = ImmutableList.copyOf(filters);
                this.roaPrefixes = roaPrefixes;
                this.filteredRoaPrefixes = filteredRoaPrefixes;
                this.bgpPreviewEntries = validateBgpRisEntries(this.bgpPreviewEntries, this.roaPrefixes);
            });
        });
    }

    private void updateRoaPrefixAssertions(Collection<RoaPrefixAssertion> assertions) {
        sequential(() -> {
            final ImmutableList<RoaPrefix> roaPrefixAssertions = ImmutableList.copyOf(
                    assertions
                            .stream()
                            .map(p -> RoaPrefix.of(
                                    null,
                                    null,
                                    p.getId(),
                                    p.getComment(),
                                    p.getAsn(),
                                    new PackedIpRange(p.getPrefix()),
                                    p.getMaxPrefixLength(),
                                    p.getMaxPrefixLength() == null ? p.getPrefix().getPrefixLength() : p.getMaxPrefixLength()
                            ))
                            .iterator()
            );

            final NestedIntervalMap<IpRange, List<RoaPrefix>> updatedRoaPrefixes = recalculateRoaPrefixes(this.validatedRoaPrefixes, this.ignoreFilters, roaPrefixAssertions);
            final Map<String, ImmutableList<BgpPreviewEntry>> updatedBgpPreviewEntries = validateBgpRisEntries(this.bgpPreviewEntries, this.roaPrefixes);

            writeLocked(() -> {
                this.roaPrefixAssertions = roaPrefixAssertions;
                this.roaPrefixes = updatedRoaPrefixes;
                this.bgpPreviewEntries = updatedBgpPreviewEntries;
            });
        });
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
            final IpRange ipRange = p.getPrefix();
            List<RoaPrefix> existing = roaPrefixes.findExact(ipRange);
            if (existing == null) {
                existing = new ArrayList<>(1);
                roaPrefixes.put(ipRange, existing);
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
                    final IpRange ipRange = p.getPrefix();
                    List<RoaPrefix> existing = roaPrefixes.findExact(ipRange);
                    if (existing == null) {
                        existing = new ArrayList<>(1);
                        roaPrefixes.put(ipRange, existing);
                    }
                    existing.add(p);
                });
        return roaPrefixes;
    }

    private <T> Map<T, ImmutableList<BgpPreviewEntry>> validateBgpRisEntries(Map<T, ImmutableList<BgpPreviewEntry>> bgpRisEntries, IntervalMap<IpRange, List<RoaPrefix>> roaPrefixes) {
        return bgpRisEntries.entrySet().stream().collect(
                Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> validateBgpRisEntries(entry.getValue(), roaPrefixes))
        );
    }

    private ImmutableList<BgpPreviewEntry> validateBgpRisEntries(
            ImmutableList<BgpPreviewEntry> bgpRisEntries,
            IntervalMap<IpRange, List<RoaPrefix>> roaPrefixes
    ) {
        final Pair<ImmutableList<BgpPreviewEntry>, Long> timed = Time.timed(() -> {
            final ImmutableList.Builder<BgpPreviewEntry> builder = ImmutableList.builder();
            bgpRisEntries.parallelStream()
                    .map(bgpRisEntry -> bgpRisEntry.ofValidity(validateBgpRisEntry(roaPrefixes, bgpRisEntry)))
                    .forEachOrdered(builder::add);
            return builder.build();
        });

        // FIXME This logging is strange
        log.debug(
                "validateBgpRisEntries duration: {} ms ({} RIS entries, {} validated ROA prefixes, {} ignore filters, {} ROA prefix assertions)",
                timed.getRight(),
                bgpRisEntries.size(),
                validatedRoaPrefixes.size(),
                ignoreFilters.size(),
                roaPrefixAssertions.size()
        );
        return timed.getLeft();
    }

    private static Validity validateBgpRisEntry(
            IntervalMap<IpRange, List<RoaPrefix>> roaPrefixes,
            BgpPreviewEntry bgpRisEntry
    ) {
        Validity validity = Validity.UNKNOWN;
        final int bgpPrefixLength = bgpRisEntry.getPrefix().getPrefixLength();
        for (List<RoaPrefix> rs : roaPrefixes.findExactAndAllLessSpecific(bgpRisEntry.getPrefix())) {
            for (RoaPrefix r : rs) {
                if (r.getAsn().longValue() == bgpRisEntry.origin) {
                    if (r.getEffectiveLength() < bgpPrefixLength) {
                        validity = Validity.INVALID_LENGTH;
                    } else {
                        return Validity.VALID;
                    }
                } else if (validity != Validity.INVALID_LENGTH) {
                    validity = Validity.INVALID_ASN;
                }
            }
        }
        return validity;
    }

    private static Validity validateMatchingBgpRisEntry(RoaPrefix matchingRoaPrefix, BgpPreviewEntry bgpRisEntry) {
        if (matchingRoaPrefix.getAsn().longValue() == bgpRisEntry.origin) {
            if (matchingRoaPrefix.getEffectiveLength() < bgpRisEntry.getPrefix().getPrefixLength()) {
                return Validity.INVALID_LENGTH;
            }
            return Validity.VALID;
        }
        return Validity.INVALID_ASN;
    }

    public BgpValidityWithFilteredResource validity(final Asn origin, final IpRange prefix) {
        final Pair<BgpValidity, BgpValidity> p = readLocked(() -> Pair.of(
                validity(origin, prefix, this.roaPrefixes),
                validity(origin, prefix, this.filteredRoaPrefixes)
        ));

        final BgpValidity roas = p.getLeft();
        final BgpValidity filtered = p.getRight();

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
                .flatMap(Collection::stream)
                .map(r -> {
                    final BgpPreviewEntry bgpPreviewEntry = BgpPreviewEntry.of(origin, prefix, Validity.UNKNOWN);
                    final Validity validity = validateMatchingBgpRisEntry(r, bgpPreviewEntry);
                    return Pair.of(r, validity);
                })
                .sorted(comparingInt(p -> {
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

        final Validity validity = matchingRoaPrefixes.stream().findFirst().map(Pair::getRight).orElse(Validity.UNKNOWN);

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

    public List<BgpRisDump> getBgpDumps() {
        return readLocked(() -> bgpRisDumps);
    }
}
