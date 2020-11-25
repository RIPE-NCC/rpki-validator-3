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
import io.swagger.annotations.ApiModelProperty;
import lombok.extern.slf4j.Slf4j;
import net.ripe.ipresource.Asn;
import net.ripe.ipresource.IpRange;
import net.ripe.ipresource.Ipv4Address;
import net.ripe.ipresource.Ipv6Address;
import net.ripe.ipresource.etree.IntervalMap;
import net.ripe.ipresource.etree.IpResourceIntervalStrategy;
import net.ripe.ipresource.etree.NestedIntervalMap;
import net.ripe.rpki.validator3.api.Paging;
import net.ripe.rpki.validator3.api.SearchTerm;
import net.ripe.rpki.validator3.api.Sorting;
import net.ripe.rpki.validator3.api.ignorefilters.IgnoreFilter;
import net.ripe.rpki.validator3.api.ignorefilters.IgnoreFilterService;
import net.ripe.rpki.validator3.api.roaprefixassertions.RoaPrefixAssertionEntity;
import net.ripe.rpki.validator3.api.roaprefixassertions.RoaPrefixAssertionsService;
import net.ripe.rpki.validator3.domain.IgnoreFiltersPredicate;
import net.ripe.rpki.validator3.domain.RoaPrefixAssertion;
import net.ripe.rpki.validator3.domain.RoaPrefixDefinition;
import net.ripe.rpki.validator3.domain.ValidatedRoaPrefix;
import net.ripe.rpki.validator3.domain.validation.ValidatedRpkiObjects;
import net.ripe.rpki.validator3.util.Locks;
import net.ripe.rpki.validator3.util.Time;
import org.apache.commons.lang.Validate;
import org.apache.commons.lang3.tuple.Pair;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
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
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Comparator.comparing;
import static java.util.Comparator.comparingInt;
import static net.ripe.rpki.validator3.api.ModelPropertyDescriptions.*;

@Service
@Slf4j
public class BgpPreviewService {

    private final int bgpRisVisibilityThreshold;

    private final BgpRisDownloader bgpRisDownloader;

    // Lock held while reading and/or writing any of the data fields below.
    private final ReentrantReadWriteLock dataLock = new ReentrantReadWriteLock();

    private List<BgpRisDump> bgpRisDumps;

    private IntervalMap<IpRange, List<RoaPrefixDefinition>> roaPrefixes = new NestedIntervalMap<>(IpResourceIntervalStrategy.getInstance());
    private IntervalMap<IpRange, List<RoaPrefixDefinition>> filteredRoaPrefixes = new NestedIntervalMap<>(IpResourceIntervalStrategy.getInstance());

    private ImmutableList<ValidatedRoaPrefix> validatedRoaPrefixes = ImmutableList.of();
    private ImmutableList<RoaPrefixAssertion> roaPrefixAssertions = ImmutableList.of();
    private ImmutableList<IgnoreFilter> ignoreFilters = ImmutableList.of();
    private Map<String, ImmutableList<BgpPreviewEntry>> bgpPreviewEntries = new TreeMap<>();

    public enum Validity {
        UNKNOWN, VALID, INVALID_ASN, INVALID_LENGTH
    }

    private static IpRange DEFAULT_IPV4_ROUTE = IpRange.parse("0.0.0.0/0");
    private static IpRange DEFAULT_IPV6_ROUTE = IpRange.parse("::/0");

    @lombok.Value(staticConstructor = "of")
    public static class BgpPreviewResult {
        int totalCount;
        long lastModified;
        Stream<BgpPreviewEntry> data;
    }

    @lombok.Value(staticConstructor = "of")
    public static class ValidatingRoa {
        @ApiModelProperty(value = ORIGIN_PROPERTY, example = ASN_EXAMPLE)
        String origin;
        String prefix;
        @ApiModelProperty(allowableValues = VALIDITY_ALLOWABLE_VALUES)
        String validity;
        Integer maxLength;
        @ApiModelProperty(SOURCE_TRUST_ANCHOR)
        String source;
        String uri;
        Long roaPrefixAssertionId;
        String roaPrefixAssertionComment;
    }

    @lombok.Value(staticConstructor = "of")
    public static class BgpValidity {
        @ApiModelProperty(value = ORIGIN_PREFIXED_PROPERTY, example = ASN_PREFIXED_EXAMPLE)
        String origin;
        String prefix;
        @ApiModelProperty(allowableValues = VALIDITY_ALLOWABLE_VALUES)
        String validity;
        List<ValidatingRoa> validatingRoas;
    }

    @lombok.Value(staticConstructor = "of")
    public static class BgpValidityWithFilteredResource {
        @ApiModelProperty(value = ORIGIN_PREFIXED_PROPERTY, example = ASN_PREFIXED_EXAMPLE)
        String origin;
        @ApiModelProperty(example = PREFIX_EXAMPLE)
        String prefix;
        @ApiModelProperty(allowableValues = VALIDITY_ALLOWABLE_VALUES)
        String validity;
        List<ValidatingRoa> validatingRoas;
        List<ValidatingRoa> filteredRoas;
    }

    @lombok.Getter
    @lombok.EqualsAndHashCode
    @lombok.AllArgsConstructor
    @lombok.ToString
    public static abstract class BgpPreviewEntry {
        // store it in memory optimised way, as we are going to store a lot of these objects in memory
        protected final int origin;
        protected final Validity validity;

        public static BgpPreviewEntry of(Asn origin, IpRange prefix, Validity validity) {
            switch (prefix.getType()) {
                case IPv4:
                    return BgpPreviewEntry4.of(origin, validity, prefix);
                case IPv6:
                    return BgpPreviewEntry6.of(origin, validity, prefix);
                default:
                    throw new IllegalArgumentException("invalid IP prefix type: " + prefix.getType());
            }
        }

        private static Predicate<BgpPreviewEntry> matches(SearchTerm searchTerm) {
            if (searchTerm == null) {
                return x -> true;
            }
            if (searchTerm.asAsn() != null) {
                Long asn = searchTerm.asAsn();
                return x -> asn == Integer.toUnsignedLong(x.origin);
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

        public abstract IpRange getPrefix();
        abstract BgpPreviewEntry ofValidity(Validity validity);

        public Asn getOrigin() {
            return new Asn(Integer.toUnsignedLong(origin));
        }

    }

    @lombok.Value
    @lombok.EqualsAndHashCode(callSuper = true)
    @lombok.ToString(callSuper = true)
    public static class BgpPreviewEntry4 extends BgpPreviewEntry {
        short prefixLength;
        int prefix;

        private BgpPreviewEntry4(int origin, Validity validity, short prefixLength, int prefix) {
            super(origin, validity);
            this.prefixLength = prefixLength;
            this.prefix = prefix;
        }

        public static BgpPreviewEntry4 of(Asn origin, Validity validity, IpRange prefix) {
            return new BgpPreviewEntry4((int) origin.longValue(), validity, (short) prefix.getPrefixLength(), (int) ((Ipv4Address) prefix.getStart()).longValue());
        }

        public IpRange getPrefix() {
            return IpRange.prefix(new Ipv4Address(Integer.toUnsignedLong(prefix)), prefixLength);
        }

        BgpPreviewEntry ofValidity(Validity validity) {
            return new BgpPreviewEntry4(origin, validity, prefixLength, prefix);
        }

    }

    @lombok.Value
    @lombok.EqualsAndHashCode(callSuper = true)
    @lombok.ToString(callSuper = true)
    public static class BgpPreviewEntry6 extends BgpPreviewEntry {
        // The amount to add to convert a 64-bit signed value to a 64-bit unsigned
        // value when the signed value is negative. This is equal to 2^64.
        private static BigInteger TWO_TO_THE_POWER_OF_64 = BigInteger.ONE.shiftLeft(64);

        // IPv6 prefix length (0 to 128 inclusive).
        short prefixLength;

        // Store the (unsigned) 128-bit IPv6 address into two (signed) 64-bit values. We'll
        // have to use BigInteger to restore the full 128-bit version again.
        long prefixHi;
        long prefixLo;

        private BgpPreviewEntry6(int origin, Validity validity, short prefixLength, long prefixHi, long prefixLo) {
            super(origin, validity);
            this.prefixLength = prefixLength;
            this.prefixHi = prefixHi;
            this.prefixLo = prefixLo;
        }

        public static BgpPreviewEntry6 of(Asn origin, Validity validity, IpRange prefix) {
            Validate.notNull(validity, "validity must not be null");
            Validate.isTrue(prefix.isLegalPrefix(), "bgp entry must be a valid prefix");
            BigInteger value = prefix.getStart().getValue();
            short prefixLength = (short) prefix.getPrefixLength();
            long prefixHi = value.shiftRight(64).longValue();
            long prefixLo = value.longValue();
            return new BgpPreviewEntry6((int) origin.longValue(), validity, prefixLength, prefixHi, prefixLo);
        }

        public IpRange getPrefix() {
            BigInteger prefix = BigInteger.valueOf(prefixHi);
            if (prefixHi < 0) {
                prefix = prefix.add(TWO_TO_THE_POWER_OF_64);
            }
            prefix = prefix.shiftLeft(64).add(BigInteger.valueOf(prefixLo));
            if (prefixLo < 0) {
                prefix = prefix.add(TWO_TO_THE_POWER_OF_64);
            }
            return IpRange.prefix(new Ipv6Address(prefix), prefixLength);
        }

        BgpPreviewEntry ofValidity(Validity validity) {
            Validate.notNull(validity, "validity must not be null");
            return new BgpPreviewEntry6(origin, validity, prefixLength, prefixHi, prefixLo);
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
        this.bgpRisDownloader = bgpRisDownloader;
        this.bgpRisDumps = Arrays.stream(bgpRisDumpUrls).map(url ->
                BgpRisDump.of(url, null, Optional.empty()))
                .collect(Collectors.toList());

        validatedRpkiObjects.addListener(objects -> updateValidatedRoaPrefixes(objects.stream().flatMap(x -> x.getRoaPrefixes().stream())));
        ignoreFilterService.addListener(this::updateIgnoreFilters);
        roaPrefixAssertionsService.addListener(this::updateRoaPrefixAssertions);
    }

    public synchronized void downloadRisPreview() {
        log.info("Updating BGP RIS dumps");
        final List<BgpRisDump<BgpPreviewEntry>> updated =
            Locks.locked(dataLock.readLock(), () -> bgpRisDumps)
                .stream()
                .map(dump -> bgpRisDownloader.fetch(dump, entry -> {
                    if (entry.getVisibility() >= bgpRisVisibilityThreshold && makesSenseToShowInPreview(entry)) {
                        return Stream.of(BgpPreviewEntry.of(
                            entry.getOrigin(),
                            entry.getPrefix(),
                            Validity.UNKNOWN
                        ));
                    } else {
                        return Stream.empty();
                    }
                }))
                .collect(Collectors.toList());
        updateBgpRisDump(updated);
        log.info("Finished updating BGP RIS dumps");
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

        return Locks.locked(dataLock.readLock(), () -> {
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

    public List<BgpPreviewEntry> findAffected(IpRange prefix, Integer maximumLength) {
        return Locks.locked(dataLock.readLock(), () -> bgpPreviewEntries
            .values()
            .parallelStream()
            .flatMap(Collection::stream)
            .filter(entry -> prefix.contains(entry.getPrefix()))
            .collect(Collectors.toList()));
    }

    public void updateBgpRisDump(Collection<BgpRisDump<BgpPreviewEntry>> updated) {
        Locks.locked(dataLock.writeLock(), () -> {
            final Map<String, ImmutableList<BgpPreviewEntry>> updatedDumps = new HashMap<>(this.bgpPreviewEntries);
            for (BgpRisDump<BgpPreviewEntry> dump : updated) {
                dump.getEntries().ifPresent(entries -> updatedDumps.put(dump.getUrl(), entries));
            }

            this.bgpPreviewEntries = validateBgpRisEntries(updatedDumps, this.roaPrefixes);
            this.bgpRisDumps = updated.stream()
                .map(x -> BgpRisDump.of(x.getUrl(), x.getLastModified(), Optional.empty()))
                .collect(Collectors.toList());
        });
    }

    private static boolean makesSenseToShowInPreview(BgpRisEntry entry) {
        return !DEFAULT_IPV4_ROUTE.equals(entry.getPrefix()) &&
            !DEFAULT_IPV6_ROUTE.equals(entry.getPrefix());
    }

    void updateValidatedRoaPrefixes(Stream<ValidatedRoaPrefix> prefixes) {
        Locks.locked(dataLock.writeLock(), () -> {
            this.validatedRoaPrefixes = ImmutableList.copyOf(prefixes.iterator());
            this.roaPrefixes = recalculateRoaPrefixes(this.validatedRoaPrefixes, this.ignoreFilters, this.roaPrefixAssertions);
            this.filteredRoaPrefixes = recalculateFilteredRoaPrefixes(this.validatedRoaPrefixes, this.ignoreFilters);
            this.bgpPreviewEntries = validateBgpRisEntries(this.bgpPreviewEntries, this.roaPrefixes);
        });
    }

    private void updateIgnoreFilters(Collection<IgnoreFilter> filters) {
        Locks.locked(dataLock.writeLock(), () -> {
            this.ignoreFilters = ImmutableList.copyOf(filters);
            this.roaPrefixes = recalculateRoaPrefixes(this.validatedRoaPrefixes, this.ignoreFilters, this.roaPrefixAssertions);
            this.filteredRoaPrefixes = recalculateFilteredRoaPrefixes(this.validatedRoaPrefixes, this.ignoreFilters);
            this.bgpPreviewEntries = validateBgpRisEntries(this.bgpPreviewEntries, this.roaPrefixes);
        });
    }

    private void updateRoaPrefixAssertions(Collection<RoaPrefixAssertionEntity> assertions) {
        Locks.locked(dataLock.writeLock(), () -> {
            this.roaPrefixAssertions = ImmutableList.copyOf(assertions
                    .stream()
                    .map(p -> RoaPrefixAssertion.of(p.getAsn(), p.getPrefix(), p.getMaxPrefixLength(), p.getId(), p.getComment()))
                    .iterator()
            );
            this.roaPrefixes = recalculateRoaPrefixes(this.validatedRoaPrefixes, this.ignoreFilters, this.roaPrefixAssertions);
            this.bgpPreviewEntries = validateBgpRisEntries(this.bgpPreviewEntries, this.roaPrefixes);
        });
    }

    private static NestedIntervalMap<IpRange, List<RoaPrefixDefinition>> recalculateRoaPrefixes(
            ImmutableList<ValidatedRoaPrefix> validatedRoaPrefixes,
            ImmutableList<IgnoreFilter> ignoreFilters,
            ImmutableList<RoaPrefixAssertion> roaPrefixAssertions
    ) {
        NestedIntervalMap<IpRange, List<RoaPrefixDefinition>> roaPrefixes = new NestedIntervalMap<>(IpResourceIntervalStrategy.getInstance());
        Stream.concat(
                validatedRoaPrefixes
                        .stream()
                        .filter(new IgnoreFiltersPredicate(ignoreFilters.stream()).negate()),
                roaPrefixAssertions.stream()
        ).forEach(p -> {
            final IpRange ipRange = p.getPrefix();
            List<RoaPrefixDefinition> existing = roaPrefixes.findExact(ipRange);
            if (existing == null) {
                existing = new ArrayList<>(1);
                roaPrefixes.put(ipRange, existing);
            }
            existing.add(p);
        });
        return roaPrefixes;
    }

    private static NestedIntervalMap<IpRange, List<RoaPrefixDefinition>> recalculateFilteredRoaPrefixes(
            ImmutableList<ValidatedRoaPrefix> validatedRoaPrefixes,
            ImmutableList<IgnoreFilter> ignoreFilters
    ) {
        NestedIntervalMap<IpRange, List<RoaPrefixDefinition>> roaPrefixes = new NestedIntervalMap<>(IpResourceIntervalStrategy.getInstance());
        validatedRoaPrefixes
                .stream()
                .filter(new IgnoreFiltersPredicate(ignoreFilters.stream()))
                .forEach(p -> {
                    final IpRange ipRange = p.getPrefix();
                    List<RoaPrefixDefinition> existing = roaPrefixes.findExact(ipRange);
                    if (existing == null) {
                        existing = new ArrayList<>(1);
                        roaPrefixes.put(ipRange, existing);
                    }
                    existing.add(p);
                });
        return roaPrefixes;
    }

    private <T> Map<T, ImmutableList<BgpPreviewEntry>> validateBgpRisEntries(Map<T, ImmutableList<BgpPreviewEntry>> bgpRisEntries, IntervalMap<IpRange, List<RoaPrefixDefinition>> roaPrefixes) {
        return bgpRisEntries.entrySet().stream().collect(
                Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> validateBgpRisEntries(entry.getValue(), roaPrefixes))
        );
    }

    private ImmutableList<BgpPreviewEntry> validateBgpRisEntries(
            ImmutableList<BgpPreviewEntry> bgpRisEntries,
            IntervalMap<IpRange, List<RoaPrefixDefinition>> roaPrefixes
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
            IntervalMap<IpRange, List<RoaPrefixDefinition>> roaPrefixes,
            BgpPreviewEntry bgpRisEntry
    ) {
        Validity validity = Validity.UNKNOWN;
        final int bgpPrefixLength = bgpRisEntry.getPrefix().getPrefixLength();
        for (List<RoaPrefixDefinition> rs : roaPrefixes.findExactAndAllLessSpecific(bgpRisEntry.getPrefix())) {
            for (RoaPrefixDefinition r : rs) {
                if (r.getAsn() == Integer.toUnsignedLong(bgpRisEntry.origin)) {
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

    private static Validity validateMatchingBgpRisEntry(RoaPrefixDefinition matchingRoaPrefix, BgpPreviewEntry bgpRisEntry) {
        if (matchingRoaPrefix.getAsn() == Integer.toUnsignedLong(bgpRisEntry.origin)) {
            if (matchingRoaPrefix.getEffectiveLength() < bgpRisEntry.getPrefix().getPrefixLength()) {
                return Validity.INVALID_LENGTH;
            }
            return Validity.VALID;
        }
        return Validity.INVALID_ASN;
    }

    public BgpValidityWithFilteredResource validity(final Asn origin, final IpRange prefix) {
        final Pair<BgpValidity, BgpValidity> p = Locks.locked(dataLock.readLock(),
            () -> Pair.of(
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

    private BgpValidity validity(final Asn origin, final IpRange prefix, final IntervalMap<IpRange, List<RoaPrefixDefinition>> prefixes) {
        final List<Pair<RoaPrefixDefinition, Validity>> matchingRoaPrefixes = prefixes.findExactAndAllLessSpecific(prefix)
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
                    final RoaPrefixDefinition r = p.getLeft();
                    if (r instanceof ValidatedRoaPrefix) {
                        ValidatedRoaPrefix roaPrefix = (ValidatedRoaPrefix) r;
                        return roaPrefix.getLocations().stream().map(loc -> ValidatingRoa.of(
                                String.valueOf(roaPrefix.getAsn()),
                                roaPrefix.getPrefix().toString(),
                                p.getRight().toString(),
                                roaPrefix.getMaximumLength(),
                                roaPrefix.getTrustAnchor() == null ? null : roaPrefix.getTrustAnchor().getName(),
                                loc,
                                null,
                                null));
                    } else if (r instanceof RoaPrefixAssertion) {
                        RoaPrefixAssertion roaPrefix = (RoaPrefixAssertion) r;
                        return Stream.of(ValidatingRoa.of(
                                String.valueOf(roaPrefix.getAsn()),
                                roaPrefix.getPrefix().toString(),
                                p.getRight().toString(),
                                roaPrefix.getMaximumLength(),
                                "Whitelist",
                                null,
                                roaPrefix.getRoaPrefixAssertionId(),
                                roaPrefix.getComment()
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
        return Locks.locked(dataLock.readLock(), () -> bgpRisDumps);
    }
}
