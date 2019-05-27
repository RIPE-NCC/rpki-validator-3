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
package net.ripe.rpki.validator3.domain.validation;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.ripe.ipresource.Asn;
import net.ripe.ipresource.IpRange;
import net.ripe.rpki.commons.crypto.x509cert.X509CertificateUtil;
import net.ripe.rpki.commons.crypto.x509cert.X509RouterCertificate;
import net.ripe.rpki.commons.validation.ValidationResult;
import net.ripe.rpki.validator3.api.Paging;
import net.ripe.rpki.validator3.api.SearchTerm;
import net.ripe.rpki.validator3.api.Sorting;
import net.ripe.rpki.validator3.domain.RoaPrefixDefinition;
import net.ripe.rpki.validator3.storage.data.Key;
import net.ripe.rpki.validator3.storage.data.Ref;
import net.ripe.rpki.validator3.storage.data.RpkiObject;
import net.ripe.rpki.validator3.storage.data.TrustAnchor;
import net.ripe.rpki.validator3.storage.data.validation.CertificateTreeValidationRun;
import net.ripe.rpki.validator3.storage.lmdb.Lmdb;
import net.ripe.rpki.validator3.storage.lmdb.LmdbTx;
import net.ripe.rpki.validator3.storage.stores.RpkiObjects;
import net.ripe.rpki.validator3.storage.stores.TrustAnchors;
import net.ripe.rpki.validator3.storage.stores.ValidationRuns;
import net.ripe.rpki.validator3.util.Locks;
import net.ripe.rpki.validator3.util.Time;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.stream.Stream;

@Component
@Slf4j
public class ValidatedRpkiObjects {

    private final List<Consumer<Collection<RoaPrefixesAndRouterCertificates>>> listeners = new ArrayList<>();

    private Map<Long, RoaPrefixesAndRouterCertificates> validatedObjectsByTrustAnchor = new HashMap<>();

    @Autowired
    private RpkiObjects rpkiObjects;

    @Autowired
    private TrustAnchors trustAnchors;

    @Autowired
    private ValidationRuns validationRuns;

    @Autowired
    private Lmdb lmdb;

    private ReentrantReadWriteLock dataLock = new ReentrantReadWriteLock();

    @PostConstruct
    private void initialize() {
        Long t = Time.timed(() -> lmdb.readTx0(tx ->
                validationRuns.findLatestSuccessful(tx, CertificateTreeValidationRun.class)
                        .forEach(vr -> {
                            final Set<Key> associatedPks = validationRuns.findAssociatedPks(tx, vr);
                            updateByKey(tx, vr.getTrustAnchor(), associatedPks);
                        })));
        log.info("Initialised in {}ms", t);
    }

    void updateByKey(LmdbTx.Read tx, Ref<TrustAnchor> trustAnchor, Collection<Key> rpkiObjectsKeys) {
        Long t = Time.timed(() ->
                trustAnchors.get(tx, trustAnchor.key())
                        .map(ta -> {
                            TrustAnchorData trustAnchorData = TrustAnchorData.of(trustAnchor.key().asLong(), ta.getName());
                            Stream<RpkiObject> roaStream = streamByType(tx, rpkiObjectsKeys, RpkiObject.Type.ROA);
                            Stream<RpkiObject> routerCertStream = streamByType(tx, rpkiObjectsKeys, RpkiObject.Type.ROUTER_CER);
                            return RoaPrefixesAndRouterCertificates.of(
                                    toRoaPrefixes(tx, trustAnchorData, roaStream),
                                    toRouterCertificates(tx, trustAnchorData, routerCertStream)
                            );
                        })
                        .ifPresent(roaPrefixesAndRouterCertificates -> {
                            log.info("updating validation objects cache for trust anchor {} with {} ROA prefixes and {} router certificates",
                                    trustAnchor,
                                    roaPrefixesAndRouterCertificates.getRoaPrefixes().size(),
                                    roaPrefixesAndRouterCertificates.getRouterCertificates().size()
                            );
                            Locks.locked(dataLock.writeLock(), () ->
                                    validatedObjectsByTrustAnchor.put(trustAnchor.key().asLong(), roaPrefixesAndRouterCertificates));
                            notifyListeners();
                        }));
        log.info("Updated validated roas in {}ms", t);
    }

    private Stream<RpkiObject> streamByType(LmdbTx.Read tx, Collection<Key> rpkiObjectsKeys, RpkiObject.Type type) {
        final Set<Key> byType = rpkiObjects.getPkByType(tx, type);
        return rpkiObjectsKeys.stream()
                .filter(byType::contains)
                .map(k -> rpkiObjects.get(tx, k))
                .filter(Optional::isPresent)
                .map(Optional::get);
    }

    public void remove(TrustAnchor trustAnchor) {
        long trustAnchorId = trustAnchor.key().asLong();
        Locks.locked(dataLock.writeLock(), () -> {
            validatedObjectsByTrustAnchor.remove(trustAnchorId);
            notifyListeners();
        });
    }

    public ValidatedObjects<RoaPrefix> findCurrentlyValidatedRoaPrefixes() {
        return findCurrentlyValidatedRoaPrefixes(null, null, null);
    }

    public ValidatedObjects<RoaPrefix> findCurrentlyValidatedRoaPrefixes(SearchTerm searchTerm, Sorting sorting, Paging paging) {
        if (paging == null) {
            paging = Paging.of(0L, Long.MAX_VALUE);
        }
        if (sorting == null) {
            sorting = Sorting.of(Sorting.By.TA, Sorting.Direction.ASC);
        }

        Sorting finalSorting = sorting;
        Paging finalPaging = paging;
        return Locks.locked(dataLock.readLock(), () ->
                ValidatedObjects.of(
                        countRoaPrefixes(searchTerm),
                        findRoaPrefixes(searchTerm, finalSorting, finalPaging)
                ));
    }

    public ValidatedObjects<RouterCertificate> findCurrentlyValidatedRouterCertificates() {
        return Locks.locked(dataLock.readLock(), () ->
                ValidatedObjects.of(
                        countRouterCertificates(),
                        findRouterCertificates()
                ));
    }

    public void addListener(Consumer<Collection<RoaPrefixesAndRouterCertificates>> listener) {
        Locks.locked(dataLock.writeLock(), () -> {
            listeners.add(listener);
            listener.accept(validatedObjectsByTrustAnchor.values());
        });
    }

    @Value(staticConstructor = "of")
    public static class ValidatedObjects<T> {
        long totalCount;
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

    private ImmutableSet<RouterCertificate> toRouterCertificates(LmdbTx.Read tx, TrustAnchorData trustAnchor, Stream<RpkiObject> roaCertStream) {
        final Base64.Encoder encoder = Base64.getEncoder();
        ImmutableSet.Builder<RouterCertificate> builder = ImmutableSet.builder();
        roaCertStream
            .map(object -> rpkiObjects.findCertificateRepositoryObject(tx, object.key(), X509RouterCertificate.class, ValidationResult.withLocation("temporary")))
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

    private ImmutableSet<RoaPrefix> toRoaPrefixes(LmdbTx.Read tx, TrustAnchorData trustAnchor, Stream<RpkiObject> roaStream) {
        ImmutableSet.Builder<RoaPrefix> builder = ImmutableSet.builder();
        roaStream
            .flatMap(
                object -> {
                    ImmutableSortedSet<String> locations = ImmutableSortedSet.copyOf(rpkiObjects.getLocations(tx, object.key()));
                    return object.getRoaPrefixes().stream().map(prefix -> Pair.of(locations, prefix));
                }
            )
            .forEach(data -> {
                net.ripe.rpki.validator3.storage.data.RoaPrefix prefix = data.getRight();
                builder.add(RoaPrefix.of(
                    trustAnchor,
                    new Asn(prefix.getAsn()),
                    prefix.getPrefix(),
                    prefix.getMaximumLength(),
                    prefix.getEffectiveLength(),
                    data.getLeft()
                ));
            });

        return builder.build();
    }

    private ImmutableList<RoaPrefixesAndRouterCertificates> validatedObjects() {
        return Locks.locked(dataLock.readLock(), () ->
                ImmutableList.copyOf(validatedObjectsByTrustAnchor.values()));
    }

    private int countRouterCertificates() {
        return validatedObjects().stream().mapToInt(x -> x.getRouterCertificates().size()).sum();
    }

    private Stream<RouterCertificate> findRouterCertificates() {
        return validatedObjects()
            .stream()
            .flatMap(x -> x.getRouterCertificates().stream());
    }

    private long countRoaPrefixes(SearchTerm searchTerm) {
        return validatedObjects().stream()
                .flatMap(x -> x.getRoaPrefixes().stream())
                .filter(prefix -> searchTerm == null || searchTerm.test(prefix))
                .count();
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
        Locks.locked(dataLock.readLock(), () ->
                listeners.forEach(listener -> listener.accept(validatedObjectsByTrustAnchor.values())));
    }
}
