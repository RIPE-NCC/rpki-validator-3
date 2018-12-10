package net.ripe.rpki.validator3.adapter.jpa;

import lombok.extern.slf4j.Slf4j;
import net.ripe.ipresource.IpResourceSet;
import net.ripe.rpki.commons.crypto.CertificateRepositoryObject;
import net.ripe.rpki.commons.crypto.cms.roa.RoaCms;
import net.ripe.rpki.commons.crypto.x509cert.X509ResourceCertificate;
import net.ripe.rpki.validator3.IntegrationTest;
import net.ripe.rpki.validator3.domain.RpkiObject;
import net.ripe.rpki.validator3.domain.RpkiObjects;
import net.ripe.rpki.validator3.util.Hex;
import net.ripe.rpki.validator3.util.Time;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit4.SpringRunner;

import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(SpringRunner.class)
@IntegrationTest
@Transactional
@Slf4j
@Ignore
public class JPARpkiObjectsTest {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private RpkiObjects rpkiObjects;

    @Test
    public void should_not_throw_exceptions_and_get_objects() {
        Pair<List<Pair<RpkiObject, CertificateRepositoryObject>>, Long> timed = Time.timed(() -> rpkiObjects.findEager(RpkiObject.Type.CER));
        List<Pair<RpkiObject, CertificateRepositoryObject>> certs = timed.getKey();
        assertFalse(certs.isEmpty());



        Pair<List<Pair<RpkiObject, CertificateRepositoryObject>>, Long> timed1 = Time.timed(() -> rpkiObjects.findEager(RpkiObject.Type.ROA));
        List<Pair<RpkiObject, CertificateRepositoryObject>> roas = timed1.getKey();
        Set<String> collect = roas.stream()
                .filter(p -> p.getRight() instanceof RoaCms)
                .map(p -> Hex.format(((RoaCms) p.getRight()).getCertificate().getAuthorityKeyIdentifier()))
                .collect(Collectors.toSet());

        final List<X509ResourceCertificate> bottomLayer = certs.parallelStream()
                .filter(p -> p.getRight() instanceof X509ResourceCertificate)
                .filter(p -> {
                    final String ski = Hex.format(((X509ResourceCertificate) p.getRight()).getSubjectKeyIdentifier());
                    return collect.contains(ski);
                })
                .map(p -> (X509ResourceCertificate) p.getRight())
                .collect(Collectors.toList());

        assertTrue(collect.size() > 0);
        assertTrue(bottomLayer.size() > 0);

    }

    @Test
    public void should_do_it() {
        final IpResourceSet ipResources = new IpResourceSet();
        final Set<String> roaAKIs = rpkiObjects.findEager(RpkiObject.Type.ROA).stream()
                .filter(p -> p.getRight() instanceof RoaCms)
                .map(p -> Hex.format(((RoaCms) p.getRight()).getCertificate().getAuthorityKeyIdentifier()))
                .collect(Collectors.toSet());

        final List<X509ResourceCertificate> allCerts = rpkiObjects.findEager(RpkiObject.Type.CER)
                .stream()
                .filter(p -> p.getRight() instanceof X509ResourceCertificate)
                .map(p -> (X509ResourceCertificate) p.getRight())
                .collect(Collectors.toList());

        final List<X509ResourceCertificate> bottomLayer = allCerts
                .stream()
                .filter(c -> !c.isRoot() && roaAKIs.contains(Hex.format(c.getSubjectKeyIdentifier())))
                .collect(Collectors.toList());


        final Map<String, Set<X509ResourceCertificate>> byAki = allCerts.stream().collect(Collectors.toMap(
                c -> Hex.format(c.getAuthorityKeyIdentifier()),
                c -> oneElem(c),
                (c1, c2) -> { c1.addAll(c2); return c1; }));

        final Map<String, X509ResourceCertificate> bySki = allCerts.stream().collect(Collectors.toMap(
                c -> Hex.format(c.getSubjectKeyIdentifier()),
                Function.identity(),
                (c1, c2) -> c1.getSerialNumber().compareTo(c2.getSerialNumber()) > 0 ? c1 : c2));

        long count = bottomLayer.stream()
                .map(c -> {
                    final String aki = Hex.format(c.getAuthorityKeyIdentifier());
                    return bySki.get(aki);
                })
                .filter(Objects::nonNull)
                .distinct()
                .flatMap(c -> {
                    final String ski = Hex.format(c.getSubjectKeyIdentifier());
                    return byAki.getOrDefault(ski, Collections.emptySet()).stream();
                })
                .count();

        assertTrue(count > 0);

    }

    private static <T> Set<T> oneElem(T c) {
        Set<T> a = new HashSet<>();
        a.add(c);
        return a;
    }
}