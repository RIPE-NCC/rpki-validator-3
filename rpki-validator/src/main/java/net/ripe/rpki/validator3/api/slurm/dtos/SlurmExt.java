package net.ripe.rpki.validator3.api.slurm.dtos;

import lombok.Data;
import net.ripe.rpki.validator3.domain.BgpSecFilter;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * The same as Slurm but also stores ids to ease the use of REST API.
 */
@Data
public class SlurmExt {
    private List<SlurmTarget> slurmTarget = new ArrayList<>();

    private Map<Long, SlurmPrefixFilter> prefixFilters = new HashMap<>();

    private Map<Long, SlurmBgpSecFilter> bgpsecFilters = new HashMap<>();

    private Map<Long, SlurmPrefixAssertion> prefixAssertions = new HashMap<>();

    private Map<Long, SlurmBgpSecAssertion> bgpsecAssertions = new HashMap<>();

    public Slurm toSlurm() {
        Slurm slurm = new Slurm();
        slurm.setLocallyAddedAssertions(new SlurmLocallyAddedAssertions(
                extract(prefixAssertions, slurmPrefixAssertionComparator),
                extract(bgpsecAssertions, slurmBgpSecAssertionComparator)));
        slurm.setValidationOutputFilters(new SlurmOutputFilters(
                extract(prefixFilters, slurmPrefixFilterComparator),
                extract(bgpsecFilters, slurmBgpSecFilterComparator)));
        slurm.setSlurmTarget(slurmTarget);
        return slurm;
    }

    private static <T> List<T> extract(Map<Long, T> s, Comparator<T> comparator) {
        return s.values().stream().sorted(comparator).collect(Collectors.toList());
    }

    private static <T> Map<Long, T> addIds(List<T> s, AtomicLong idSeq, Comparator<T> comparator) {
        return s.stream()
                .sorted(comparator)
                .map(v -> Pair.of(idSeq.getAndIncrement(), v))
                .collect(Collectors.toMap(Pair::getLeft, Pair::getRight));
    }

    private static Comparator<SlurmPrefixFilter> slurmPrefixFilterComparator =
            Comparator.comparing(SlurmPrefixFilter::getAsn)
                    .thenComparing(SlurmPrefixFilter::getPrefix);

    private static Comparator<SlurmBgpSecFilter> slurmBgpSecFilterComparator =
            Comparator.comparing(SlurmBgpSecFilter::getAsn)
                    .thenComparing(SlurmBgpSecFilter::getSki);

    private static Comparator<SlurmPrefixAssertion> slurmPrefixAssertionComparator =
            Comparator.comparing(SlurmPrefixAssertion::getAsn)
                    .thenComparing(SlurmPrefixAssertion::getPrefix)
                    .thenComparing(SlurmPrefixAssertion::getMaxPrefixLength);

    private static Comparator<SlurmBgpSecAssertion> slurmBgpSecAssertionComparator =
            Comparator.comparing(SlurmBgpSecAssertion::getAsn)
                    .thenComparing(SlurmBgpSecAssertion::getPublicKey)
                    .thenComparing(SlurmBgpSecAssertion::getSki);

    public static SlurmExt fromSlurm(Slurm slurm, AtomicLong idSeq) {
        final SlurmExt slurmExt = new SlurmExt();

        // always sort SLURM entries so that they have stable IDs
        slurmExt.setPrefixFilters(addIds(slurm.getValidationOutputFilters().getPrefixFilters(), idSeq, slurmPrefixFilterComparator));
        slurmExt.setBgpsecFilters(addIds(slurm.getValidationOutputFilters().getBgpsecFilters(), idSeq, slurmBgpSecFilterComparator));
        slurmExt.setPrefixAssertions(addIds(slurm.getLocallyAddedAssertions().getPrefixAssertions(), idSeq, slurmPrefixAssertionComparator));
        slurmExt.setBgpsecAssertions(addIds(slurm.getLocallyAddedAssertions().getBgpsecAssertions(), idSeq, slurmBgpSecAssertionComparator));
        return slurmExt;
    }

    public SlurmExt copy() {
        // we never change the elements in place,
        // so it's fine to shallow copy the lists
        final SlurmExt slurmExt = new SlurmExt();
        slurmExt.setPrefixFilters(new HashMap<>(prefixFilters));
        slurmExt.setBgpsecFilters(new HashMap<>(bgpsecFilters));
        slurmExt.setPrefixAssertions(new HashMap<>(prefixAssertions));
        slurmExt.setBgpsecAssertions(new HashMap<>(bgpsecAssertions));
        return slurmExt;
    }
}
