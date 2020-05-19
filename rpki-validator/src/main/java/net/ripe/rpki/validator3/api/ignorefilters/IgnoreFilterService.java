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
package net.ripe.rpki.validator3.api.ignorefilters;

import lombok.extern.slf4j.Slf4j;
import net.ripe.ipresource.Asn;
import net.ripe.ipresource.IpRange;
import net.ripe.ipresource.IpResource;
import net.ripe.rpki.validator3.api.Paging;
import net.ripe.rpki.validator3.api.SearchTerm;
import net.ripe.rpki.validator3.api.Sorting;
import net.ripe.rpki.validator3.api.slurm.SlurmStore;
import net.ripe.rpki.validator3.api.slurm.dtos.Slurm;
import net.ripe.rpki.validator3.api.slurm.dtos.SlurmExt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

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
@Validated
@Slf4j
public class IgnoreFilterService {
    private final Object listenersLock = new Object();
    private final List<Consumer<Collection<IgnoreFilter>>> listeners = new ArrayList<>();

    private final SlurmStore slurmStore;

    @Autowired
    public IgnoreFilterService(SlurmStore slurmStore) {
        this.slurmStore = slurmStore;
    }

    public long execute(@Valid AddIgnoreFilter command) {
        return slurmStore.updateWith(slurmExt -> {
            final Slurm.SlurmPrefixFilter ignoreFilter = new Slurm.SlurmPrefixFilter();
            if (command.getAsn() != null) {
                ignoreFilter.setAsn(Asn.parse(command.getAsn()).longValue());
            }
            if (command.getPrefix() != null) {
                ignoreFilter.setPrefix(IpRange.parse(command.getPrefix()));
            }
            ignoreFilter.setComment(command.getComment());

            final long id = slurmStore.nextId();
            slurmExt.getPrefixFilters().put(id, ignoreFilter);

            log.info("added ignore filter '{}'", ignoreFilter);
            return id;
        });
    }

    public void remove(long ignoreFilterId) {
        Boolean notify = slurmStore.updateWith(slurmExt ->
                slurmExt.getPrefixFilters().remove(ignoreFilterId) != null);
        if (notify) {
            notifyListeners();
        }
    }

    public void clear() {
        slurmStore.updateWith(IgnoreFilterService::clearAll);
        notifyListeners();
    }

    public void addListener(Consumer<Collection<IgnoreFilter>> listener) {
        synchronized (listenersLock) {
            List<IgnoreFilter> filters = all().collect(Collectors.toList());
            listener.accept(filters);
            listeners.add(listener);
        }
    }

    private void notifyListeners() {
        synchronized (listenersLock) {
            List<IgnoreFilter> filters = all().collect(Collectors.toList());
            listeners.forEach(listener -> listener.accept(filters));
        }
    }


    public Stream<IgnoreFilter> all() {
        return slurmStore.read().getPrefixFilters().entrySet().stream()
                .map(e -> makeIgnoreFilter(e.getKey(), e.getValue()));
    }

    public Stream<IgnoreFilter> find(SearchTerm searchTerm, Sorting sorting, Paging paging) {
        Stream<Map.Entry<Long, Slurm.SlurmPrefixFilter>> all = slurmStore.read().getPrefixFilters().entrySet().stream();
        all = applySearch(searchTerm, all).sorted(toOrderSpecifier(sorting));
        if (paging != null) {
            all = paging.apply(all);
        }
        return all.map(e -> makeIgnoreFilter(e.getKey(), e.getValue()));
    }

    private IgnoreFilter makeIgnoreFilter(Long id, Slurm.SlurmPrefixFilter value) {
        return new IgnoreFilter(id, value.getAsn(), value.getPrefix(), value.getComment());
    }

    public Stream<Map.Entry<Long, Slurm.SlurmPrefixFilter>> applySearch(SearchTerm searchTerm, Stream<Map.Entry<Long, Slurm.SlurmPrefixFilter>> all) {
        if (searchTerm != null) {
            if (searchTerm.asAsn() != null) {
                all = all
                    .filter(pf -> pf.getValue().getAsn() != null)
                    .filter(pf -> pf.getValue().getAsn().longValue() == searchTerm.asAsn().longValue());
            } else if (searchTerm.asIpRange() != null) {
                all = all
                    .filter(pf -> pf.getValue().getPrefix() != null)
                    .filter(pf -> pf.getValue().getPrefix().overlaps(searchTerm.asIpRange()));
            } else {
                all = all
                    .filter(pf -> pf.getValue().getComment() != null)
                    .filter(pf -> pf.getValue().getComment().toLowerCase().contains(searchTerm.asString().toLowerCase()));
            }
        }
        return all;
    }

    public long count(SearchTerm searchTerm) {
        return applySearch(searchTerm, slurmStore.read().getPrefixFilters().entrySet().stream()).count();
    }

    public IgnoreFilter get(long id) {
        final Slurm.SlurmPrefixFilter slurmPrefixFilter = slurmStore.read().getPrefixFilters().get(id);
        if (slurmPrefixFilter == null) {
            return null;
        }
        return makeIgnoreFilter(id, slurmPrefixFilter);
    }

    private Comparator<Map.Entry<Long, Slurm.SlurmPrefixFilter>> toOrderSpecifier(Sorting sorting) {
        if (sorting == null) {
            sorting = Sorting.of(Sorting.By.ASN, Sorting.Direction.ASC);
        }
        Comparator<Map.Entry<Long, Slurm.SlurmPrefixFilter>> comparator;
        switch (sorting.getBy()) {
            case PREFIX:
                comparator = Comparator.comparing(e -> e.getValue().getPrefix(), Comparator.nullsFirst(IpResource::compareTo));
                break;
            case COMMENT:
                comparator = Comparator.comparing(e -> e.getValue().getComment(), Comparator.nullsFirst(String::compareTo));
                break;
            case ASN:
            default:
                comparator = Comparator.comparing(e -> e.getValue().getAsn(), Comparator.nullsFirst(Long::compareTo));
                break;
        }
        return sorting.getDirection() == Sorting.Direction.DESC ? comparator.reversed() : comparator;
    }

    private static void clearAll(SlurmExt slurmExt) {
        slurmExt.getPrefixFilters().clear();
    }
}
