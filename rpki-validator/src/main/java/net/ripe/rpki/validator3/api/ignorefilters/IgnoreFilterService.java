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
import net.ripe.rpki.validator3.domain.IgnoreFilter;
import net.ripe.rpki.validator3.domain.IgnoreFilters;
import net.ripe.rpki.validator3.util.Transactions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import javax.transaction.Transactional;
import javax.validation.Valid;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
@Transactional
@Validated
@Slf4j
public class IgnoreFilterService {
    private final Object listenersLock = new Object();
    private final List<Consumer<Collection<IgnoreFilter>>> listeners = new ArrayList<>();

    @Autowired
    private IgnoreFilters ignoreFilters;

    public long execute(@Valid AddIgnoreFilter command) {
        IgnoreFilter ignoreFilter = new IgnoreFilter();
        if (command.getAsn() != null) {
            ignoreFilter.setAsn(Asn.parse(command.getAsn()).longValue());
        }
        ignoreFilter.setPrefix(command.getPrefix());
        ignoreFilter.setComment(command.getComment());

        return add(ignoreFilter);
    }

    long add(IgnoreFilter ignoreFilter) {
        ignoreFilters.add(ignoreFilter);
        notifyListeners();

        log.info("added ignore filter '{}'", ignoreFilter);
        return ignoreFilter.getId();
    }

    public void remove(long ignoreFilterId) {
        IgnoreFilter ignoreFilter = ignoreFilters.get(ignoreFilterId);
        if (ignoreFilter != null) {
            ignoreFilters.remove(ignoreFilter);
            notifyListeners();
        }
    }

    public Stream<IgnoreFilter> all() {
        return ignoreFilters.all();
    }

    public void clear() {
        ignoreFilters.clear();
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
        Transactions.afterCommitOnce(
            listenersLock,
            () -> {
                synchronized (listenersLock) {
                    List<IgnoreFilter> filters = all().collect(Collectors.toList());
                    listeners.forEach(listener -> listener.accept(filters));
                }
            }
        );
    }
}
