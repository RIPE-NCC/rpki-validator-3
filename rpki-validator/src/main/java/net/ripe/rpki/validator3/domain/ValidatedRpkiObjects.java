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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.ripe.ipresource.Asn;
import net.ripe.ipresource.IpRange;
import net.ripe.rpki.commons.crypto.x509cert.X509CertificateUtil;
import net.ripe.rpki.commons.crypto.x509cert.X509RouterCertificate;
import net.ripe.rpki.validator3.api.Paging;
import net.ripe.rpki.validator3.api.SearchTerm;
import net.ripe.rpki.validator3.api.Sorting;
import net.ripe.rpki.validator3.util.Transactions;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.PostConstruct;
import javax.transaction.Transactional;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
@Slf4j
public class ValidatedRpkiObjects {
    private final Object listenerLock = new Object();
    private final List<Consumer<Collection<RoaPrefixesAndRouterCertificates>>> listeners = new ArrayList<>();

    private Map<Long, RoaPrefixesAndRouterCertificates> validatedObjectsByTrustAnchor = new HashMap<>();

    @Autowired
    private RpkiObjects rpkiObjects;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @PostConstruct
    private synchronized void initialize() {
        new TransactionTemplate(transactionManager).execute((status) -> {
            Map<@NotNull @Valid TrustAnchor, List<RpkiObject>> grouped = Stream.concat(
                rpkiObjects.findCurrentlyValidated(RpkiObject.Type.ROA),
                rpkiObjects.findCurrentlyValidated(RpkiObject.Type.ROUTER_CER)
                )
                .collect(Collectors.groupingBy(
                    pair -> pair.getLeft().getTrustAnchor(),
                    Collectors.mapping(pair -> pair.getRight(), Collectors.toList())
                ));
            grouped.forEach(this::update);
            return null;
        });
    }

    @Transactional
    public void update(TrustAnchor trustAnchor, Collection<RpkiObject> rpkiObjects) {
        TrustAnchorData trustAnchorData = TrustAnchorData.of(trustAnchor.getId(), trustAnchor.getName());
        RoaPrefixesAndRouterCertificates roaPrefixesAndRouterCertificates = RoaPrefixesAndRouterCertificates.of(
            extractRoaPrefixes(trustAnchorData, rpkiObjects),
            extractRouterCertificates(trustAnchorData, rpkiObjects)
        );

        // Only update the cache after the current transaction successfully commits.
        Transactions.afterCommit(
            listenerLock,
            () -> {
                log.info("updating validation objects cache for trust anchor {} with {} ROA prefixes and {} router certificates",
                    trustAnchor,
                    roaPrefixesAndRouterCertificates.getRoaPrefixes().size(),
                    roaPrefixesAndRouterCertificates.getRouterCertificates().size()
                );

                validatedObjectsByTrustAnchor.put(trustAnchor.getId(), roaPrefixesAndRouterCertificates);

                notifyListeners();
            }
        );
    }

    @Transactional
    public void remove(TrustAnchor trustAnchor) {
        long trustAnchorId = trustAnchor.getId();
        Transactions.afterCommit(
            listenerLock,
            () -> {
                validatedObjectsByTrustAnchor.remove(trustAnchorId);
                notifyListeners();
            }
        );
    }

    public ValidatedObjects<RoaPrefix> findCurrentlyValidatedRoaPrefixes(SearchTerm searchTerm, Sorting sorting, Paging paging) {
        if (paging == null) {
            paging = Paging.of(0, Integer.MAX_VALUE);
        }
        if (sorting == null) {
            sorting = Sorting.of(Sorting.By.TA, Sorting.Direction.ASC);
        }

        return ValidatedObjects.of(
            countRoaPrefixes(searchTerm),
            findRoaPrefixes(searchTerm, sorting, paging)
        );
    }

    public ValidatedObjects<RouterCertificate> findCurrentlyValidatedRouterCertificates() {
        return ValidatedObjects.of(
            countRouterCertificates(),
            findRouterCertificates()
        );
    }

    public void addListener(Consumer<Collection<RoaPrefixesAndRouterCertificates>> listener) {
        synchronized (listenerLock) {
            listeners.add(listener);
            listener.accept(validatedObjectsByTrustAnchor.values());
        }
    }

    @Value(staticConstructor = "of")
    public static class ValidatedObjects<T> {
        int totalCount;
        Stream<T> objects;
    }

    @Value(staticConstructor = "of")
    public static class RoaPrefixesAndRouterCertificates {
        ImmutableSet<RoaPrefix> roaPrefixes;
        ImmutableSet<RouterCertificate> routerCertificates;
    }

    @Value(staticConstructor = "of")
    public static class TrustAnchorData {
        long id;
        String name;
    }

    @Value(staticConstructor = "of")
    public static class RoaPrefix implements RoaPrefixDefinition {
        TrustAnchorData trustAnchor;
        Asn asn;
        IpRange prefix;
        Integer maximumLength;
        int effectiveLength;
        ImmutableSortedSet<String> locations;
    }

    @Value(staticConstructor = "of")
    public static class RouterCertificate {
        TrustAnchorData trustAnchor;
        ImmutableList<String> asn;
        String subjectKeyIdentifier;
        String subjectPublicKeyInfo;
    }

    private ImmutableSet<RouterCertificate> extractRouterCertificates(TrustAnchorData trustAnchor, Collection<RpkiObject> rpkiObjects) {
        final Base64.Encoder encoder = Base64.getEncoder();
        ImmutableSet.Builder<RouterCertificate> builder = ImmutableSet.builder();
        rpkiObjects
            .stream()
            .filter(object -> object.getType() == RpkiObject.Type.ROUTER_CER)
            .map(object -> object.get(X509RouterCertificate.class, "temporary"))
            .filter(Optional::isPresent).map(Optional::get)
            .forEach(certificate -> {
                    final ImmutableList<String> asns = ImmutableList.copyOf(X509CertificateUtil.getAsns(certificate.getCertificate()));
                    final String ski = encoder.encodeToString(X509CertificateUtil.getSubjectKeyIdentifier(certificate.getCertificate()));
                    final String pkInfo = X509CertificateUtil.getEncodedSubjectPublicKeyInfo(certificate.getCertificate());
                    builder.add(RouterCertificate.of(
                        trustAnchor,
                        asns,
                        ski,
                        pkInfo
                    ));
                }
            );

        return builder.build();
    }

    private ImmutableSet<RoaPrefix> extractRoaPrefixes(TrustAnchorData trustAnchor, Collection<RpkiObject> rpkiObjects) {
        ImmutableSet.Builder<RoaPrefix> builder = ImmutableSet.builder();
        rpkiObjects
            .stream()
            .filter(
                object -> object.getType() == RpkiObject.Type.ROA
            )
            .flatMap(
                object -> {
                    ImmutableSortedSet<String> locations = ImmutableSortedSet.copyOf(object.getLocations());
                    return object.getRoaPrefixes().stream().map(prefix -> Pair.of(locations, prefix));
                }
            )
            .forEach(data -> {
                net.ripe.rpki.validator3.domain.RoaPrefix prefix = data.getRight();
                builder.add(RoaPrefix.of(
                    trustAnchor,
                    new Asn(prefix.getAsn()),
                    IpRange.parse(prefix.getPrefix()),
                    prefix.getMaximumLength(),
                    prefix.getEffectiveLength(),
                    data.getLeft()
                ));
            });

        return builder.build();
    }

    private synchronized ImmutableList<RoaPrefixesAndRouterCertificates> validatedObjects() {
        return ImmutableList.copyOf(validatedObjectsByTrustAnchor.values());
    }

    private int countRouterCertificates() {
        int result = 0;
        for (RoaPrefixesAndRouterCertificates x : validatedObjects()) {
            result += x.getRouterCertificates().size();
        }
        return result;
    }

    private Stream<RouterCertificate> findRouterCertificates() {
        return validatedObjects()
            .stream()
            .flatMap(x -> x.getRouterCertificates().stream());
    }

    private int countRoaPrefixes(SearchTerm searchTerm) {
        int result = 0;
        for (RoaPrefixesAndRouterCertificates x : validatedObjects()) {
            for (RoaPrefix prefix : x.getRoaPrefixes()) {
                if (searchTerm == null || searchTerm.test(prefix)) {
                    ++result;
                }
            }
        }
        return result;
    }

    private Stream<RoaPrefix> findRoaPrefixes(SearchTerm searchTerm, Sorting sorting, Paging paging) {
        return validatedObjects()
            .parallelStream()
            .flatMap(x -> x.getRoaPrefixes().stream())
            .filter(prefix -> searchTerm == null || searchTerm.test(prefix))
            .sorted(sorting.comparator())
            .skip(Math.max(0, paging.getStartFrom()))
            .limit(Math.max(1, paging.getPageSize()));
    }

    private void notifyListeners() {
        listeners.forEach(listener -> listener.accept(validatedObjectsByTrustAnchor.values()));
    }
}
