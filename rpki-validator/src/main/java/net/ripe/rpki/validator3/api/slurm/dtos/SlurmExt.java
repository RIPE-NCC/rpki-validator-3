package net.ripe.rpki.validator3.api.slurm.dtos;

import lombok.Data;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * The same as Slurm but also stores ids to ease the use of REST API.
 */
@Data
public class SlurmExt {
    private List<SlurmTarget> slurmTarget;

    private List<Pair<Long, SlurmPrefixFilter>> prefixFilters;

    private List<Pair<Long, SlurmBgpSecFilter>> bgpsecFilters;

    private List<Pair<Long, SlurmPrefixAssertion>> prefixAssertions;

    private List<Pair<Long, SlurmBgpSecAssertion>> bgpsecAssertions;

    public Slurm toSlurm() {
        Slurm slurm = new Slurm();
        slurm.setLocallyAddedAssertions(new SlurmLocallyAddedAssertions(extract(prefixAssertions), extract(bgpsecAssertions)));
        slurm.setValidationOutputFilters(new SlurmOutputFilters(extract(prefixFilters), extract(bgpsecFilters)));
        slurm.setSlurmTarget(slurmTarget);
        return slurm;
    }

    private static <T> List<T> extract(List<Pair<Long, T>> s) {
        return s.stream().map(Pair::getRight).collect(Collectors.toList());
    }

    private static <T> List<Pair<Long, T>> addIds(List<T> s,  AtomicLong idSeq) {
        return s.stream().map(v -> Pair.of(idSeq.getAndIncrement(), v)).collect(Collectors.toList());
    }


    public static SlurmExt fromSlurm(Slurm slurm, AtomicLong idSeq) {
        final SlurmExt slurmExt = new SlurmExt();
        slurmExt.setPrefixFilters(addIds(slurm.getValidationOutputFilters().getPrefixFilters(), idSeq));
        slurmExt.setBgpsecFilters(addIds(slurm.getValidationOutputFilters().getBgpsecFilters(), idSeq));
        slurmExt.setPrefixAssertions(addIds(slurm.getLocallyAddedAssertions().getPrefixAssertions(), idSeq));
        slurmExt.setBgpsecAssertions(addIds(slurm.getLocallyAddedAssertions().getBgpsecAssertions(), idSeq));
        return slurmExt;
    }

    public SlurmExt copy() {
        // we never change the elements in place,
        // so it's fine to shallow copy the lists
        final SlurmExt slurmExt = new SlurmExt();
        slurmExt.setPrefixFilters(new ArrayList<>(prefixFilters));
        slurmExt.setBgpsecFilters(new ArrayList<>(bgpsecFilters));
        slurmExt.setPrefixAssertions(new ArrayList<>(prefixAssertions));
        slurmExt.setBgpsecAssertions(new ArrayList<>(bgpsecAssertions));
        return slurmExt;
    }
}
