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
package net.ripe.rpki.validator3.api.roaprefixassertions;

import lombok.extern.slf4j.Slf4j;
import net.ripe.ipresource.Asn;
import net.ripe.ipresource.IpRange;
import net.ripe.rpki.validator3.api.Paging;
import net.ripe.rpki.validator3.api.SearchTerm;
import net.ripe.rpki.validator3.api.Sorting;
import net.ripe.rpki.validator3.api.slurm.SlurmStore;
import net.ripe.rpki.validator3.api.slurm.dtos.Slurm;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import javax.transaction.Transactional;
import javax.validation.Valid;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
@Transactional
@Validated
@Slf4j
public class RoaPrefixAssertionsService {
    private final Object listenerLock = new Object();
    private final List<Consumer<Collection<RoaPrefixAssertion>>> listeners = new ArrayList<>();

    private final SlurmStore slurmStore;

    @Autowired
    public RoaPrefixAssertionsService(SlurmStore slurmStore) {
        this.slurmStore = slurmStore;
    }

    public long execute(@Valid AddRoaPrefixAssertion command) {
        final Long rpId = slurmStore.updateWith(slurmExt -> {
            final Slurm.SlurmPrefixAssertion prefixAssertion = new Slurm.SlurmPrefixAssertion();
            prefixAssertion.setAsn(Asn.parse(command.getAsn()));
            prefixAssertion.setPrefix(IpRange.parse(command.getPrefix()));
            prefixAssertion.setComment(command.getComment());
            prefixAssertion.setMaxPrefixLength(command.getMaximumLength());

            final long id = slurmStore.nextId();
            slurmExt.getPrefixAssertions().put(id, prefixAssertion);

            log.info("added ignore filter '{}'", prefixAssertion);
            return id;
        });
        notifyListeners();
        return rpId;
    }

    public void remove(long roaPrefixAssertionId) {
        final boolean notify = slurmStore.updateWith(slurmExt ->
                slurmExt.getPrefixAssertions().remove(roaPrefixAssertionId) != null);
        if (notify) {
            notifyListeners();
        }
    }

    public void clear() {
        slurmStore.updateWith(slurmExt -> {
            slurmExt.getPrefixAssertions().clear();
        });
        notifyListeners();
    }

    public void addListener(Consumer<Collection<RoaPrefixAssertion>> listener) {
        synchronized (listenerLock) {
            List<RoaPrefixAssertion> assertions = all().collect(Collectors.toList());
            listener.accept(assertions);
            listeners.add(listener);
        }
    }

    private void notifyListeners() {
        synchronized (listenerLock) {
            List<RoaPrefixAssertion> assertions = all().collect(Collectors.toList());
            listeners.forEach(listener -> listener.accept(assertions));
        }
    }


    public Stream<RoaPrefixAssertion> all() {
        return slurmStore.read().getPrefixAssertions().entrySet().stream()
                .map(e -> makeIgnoreFilter(e.getKey(), e.getValue()));
    }

    public Stream<RoaPrefixAssertion> find(SearchTerm searchTerm, Sorting sorting, Paging paging) {
        Stream<Map.Entry<Long, Slurm.SlurmPrefixAssertion>> all = slurmStore.read().getPrefixAssertions().entrySet().stream();
        all = applySearch(searchTerm, all).sorted(toOrderSpecifier(sorting));
        if (paging != null) {
            all = paging.apply(all);
        }
        return all.map(e -> makeIgnoreFilter(e.getKey(), e.getValue()));
    }

    private RoaPrefixAssertion makeIgnoreFilter(Long id, Slurm.SlurmPrefixAssertion value) {
        return new RoaPrefixAssertion(id, value.getAsn(), value.getPrefix(), value.getMaxPrefixLength(), value.getComment());
    }

    public Stream<Map.Entry<Long, Slurm.SlurmPrefixAssertion>> applySearch(SearchTerm searchTerm, Stream<Map.Entry<Long, Slurm.SlurmPrefixAssertion>> all) {
        if (searchTerm != null) {
            if (searchTerm.asAsn() != null) {
                all = all.filter(pf -> pf.getValue().getAsn().longValue() == searchTerm.asAsn().longValue());
            } else if (searchTerm.asIpRange() != null) {
                all = all.filter(pf -> pf.getValue().getPrefix().overlaps(searchTerm.asIpRange()));
            } else {
                all = all.filter(pf -> pf.getValue().getComment().toLowerCase().contains(searchTerm.asString().toLowerCase()));
            }
        }
        return all;
    }

    public long count(SearchTerm searchTerm) {
        return applySearch(searchTerm, slurmStore.read().getPrefixAssertions().entrySet().stream()).count();
    }

    public RoaPrefixAssertion get(long id) {
        final Slurm.SlurmPrefixAssertion prefixAssertion = slurmStore.read().getPrefixAssertions().get(id);
        if (prefixAssertion == null) {
            return null;
        }
        return makeIgnoreFilter(id, prefixAssertion);
    }

    private Comparator<Map.Entry<Long, Slurm.SlurmPrefixAssertion>> toOrderSpecifier(Sorting sorting) {
        if (sorting == null) {
            sorting = Sorting.of(Sorting.By.ASN, Sorting.Direction.ASC);
        }
        Comparator<Map.Entry<Long, Slurm.SlurmPrefixAssertion>> comparator;
        switch (sorting.getBy()) {
            case PREFIX:
                comparator = Comparator.comparing(e -> e.getValue().getPrefix());
                break;
            case MAXIMUMLENGTH:
                comparator = Comparator.comparing(e -> e.getValue().getMaxPrefixLength());
                break;
            case COMMENT:
                comparator = Comparator.comparing(e -> e.getValue().getComment());
                break;
            case ASN:
            default:
                comparator = Comparator.comparing(e -> e.getValue().getAsn());
                break;
        }
        return sorting.getDirection() == Sorting.Direction.DESC ? comparator.reversed() : comparator;
    }
}
