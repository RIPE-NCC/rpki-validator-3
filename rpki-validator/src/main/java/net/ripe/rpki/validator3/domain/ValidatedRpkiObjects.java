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
package net.ripe.rpki.validator3.domain;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.ripe.ipresource.Asn;
import net.ripe.ipresource.IpRange;
import net.ripe.rpki.validator3.api.Paging;
import net.ripe.rpki.validator3.api.SearchTerm;
import net.ripe.rpki.validator3.api.Sorting;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.PostConstruct;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
@Slf4j
public class ValidatedRpkiObjects {

    @Autowired
    private RpkiObjects rpkiObjects;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private Map<TrustAnchor, RoaPrefixesAndRouterCertificates> validatedObjectsByTrustAnchor = new HashMap<>();

    @PostConstruct
    private synchronized void initialize() {
        new TransactionTemplate(transactionManager).execute((status) -> {
            Map<@NotNull @Valid TrustAnchor, List<RpkiObject>> grouped = rpkiObjects.findCurrentlyValidated(RpkiObject.Type.ROA)
                .collect(Collectors.groupingBy(
                    pair -> pair.getKey().getTrustAnchor(),
                    Collectors.mapping(pair -> pair.getValue(), Collectors.toList())
                ));
            grouped.forEach(this::update);
            return null;
        });
    }

    public synchronized void update(TrustAnchor trustAnchor, Collection<RpkiObject> rpkiObjects) {
        log.info("updating validation objects cache for trust anchor {} with {} objects", trustAnchor, rpkiObjects.size());

        ImmutableSet.Builder<RpkiObjects.RoaPrefix> builder = ImmutableSet.builder();
        rpkiObjects
            .stream()
            .sequential()
            .filter(
                object -> object.getType() == RpkiObject.Type.ROA || object.getType() == RpkiObject.Type.ROUTER_CER
            )
            .flatMap(
                object -> object.getRoaPrefixes().stream().map(prefix -> Pair.of(object.getLocations().first(), prefix))
            )
            .forEach(data -> {
                RoaPrefix prefix = data.getRight();
                builder.add(new RpkiObjects.RoaPrefix(
                    new Asn(prefix.getAsn()),
                    IpRange.parse(prefix.getPrefix()),
                    prefix.getEffectiveLength(),
                    trustAnchor.getName(),
                    data.getLeft()
                ));
            });

        validatedObjectsByTrustAnchor.put(trustAnchor, RoaPrefixesAndRouterCertificates.of(builder.build()));
    }

    public synchronized ImmutableMap<TrustAnchor, RoaPrefixesAndRouterCertificates> validatedObjects() {
        return ImmutableMap.copyOf(validatedObjectsByTrustAnchor);
    }

    public int countCurrentlyValidatedRoaPrefixes(SearchTerm searchTerm) {
        int result = 0;
        for (RoaPrefixesAndRouterCertificates x : validatedObjects().values()) {
            for (RpkiObjects.RoaPrefix prefix : x.getRoaPrefixes()) {
                if (searchTerm == null || searchTerm.match(prefix)) {
                    ++result;
                }
            }
        }
        return result;
    }

    public Stream<RpkiObjects.RoaPrefix> findCurrentlyValidatedRoaPrefixes(SearchTerm searchTerm, Sorting sorting, Paging paging) {
        if (paging == null) {
            paging = Paging.of(0, Integer.MAX_VALUE);
        }
        if (sorting == null) {
            sorting = Sorting.of(Sorting.By.TA, Sorting.Direction.ASC);
        }

        return validatedObjects().values().stream().flatMap(x -> x.getRoaPrefixes().stream())
            .parallel()
            .filter(prefix -> searchTerm == null || searchTerm.match(prefix))
            .sorted(sorting.comparator())
            .skip(Math.max(0, paging.getStartFrom()))
            .limit(Math.max(1, paging.getPageSize()));
    }

    @Value(staticConstructor = "of")
    public static class RoaPrefixesAndRouterCertificates {
        ImmutableSet<RpkiObjects.RoaPrefix> roaPrefixes;
    }
}
