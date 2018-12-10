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
package net.ripe.rpki.validator3.api.rpkiobjects;

import au.com.bytecode.opencsv.CSVWriter;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.ripe.ipresource.IpResource;
import net.ripe.ipresource.IpResourceSet;
import net.ripe.ipresource.IpResourceType;
import net.ripe.rpki.commons.crypto.CertificateRepositoryObject;
import net.ripe.rpki.commons.crypto.ValidityPeriod;
import net.ripe.rpki.commons.crypto.cms.ghostbuster.GhostbustersCms;
import net.ripe.rpki.commons.crypto.cms.manifest.ManifestCms;
import net.ripe.rpki.commons.crypto.cms.roa.RoaCms;
import net.ripe.rpki.commons.crypto.crl.X509Crl;
import net.ripe.rpki.commons.crypto.x509cert.X509CertificateInformationAccessDescriptor;
import net.ripe.rpki.commons.crypto.x509cert.X509ResourceCertificate;
import net.ripe.rpki.commons.crypto.x509cert.X509RouterCertificate;
import net.ripe.rpki.commons.validation.ValidationResult;
import net.ripe.rpki.validator3.api.Api;
import net.ripe.rpki.validator3.api.ApiResponse;
import net.ripe.rpki.validator3.api.roas.ExportsController;
import net.ripe.rpki.validator3.domain.CertificateTreeValidationRun;
import net.ripe.rpki.validator3.domain.RoaPrefix;
import net.ripe.rpki.validator3.domain.RpkiObject;
import net.ripe.rpki.validator3.domain.RpkiObjects;
import net.ripe.rpki.validator3.domain.ValidationCheck;
import net.ripe.rpki.validator3.domain.ValidationRuns;
import net.ripe.rpki.validator3.util.Hex;
import net.ripe.rpki.validator3.util.Time;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;
import javax.transaction.Transactional;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.io.IOException;
import java.math.BigInteger;
import java.security.cert.X509CRLEntry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static net.ripe.rpki.validator3.domain.RpkiObject.Type.CER;

@RestController
@RequestMapping(path = "/api/rpki-objects", produces = { Api.API_MIME_TYPE, "application/json" })
@Slf4j
public class RpkiObjectController {

    @Autowired
    private RpkiObjects rpkiObjects;

    @Autowired
    private ValidationRuns validationRuns;

    @GetMapping(path = "/")
    public ResponseEntity<ApiResponse<Stream<RpkiObj>>> all() {
        final List<CertificateTreeValidationRun> vrs = validationRuns.findLatestSuccessful(CertificateTreeValidationRun.class);

        final Stream<RpkiObj> rpkiObjStream = vrs.stream().flatMap(vr -> {
            final Map<String, ValidationCheck> checkMap = vr.getValidationChecks().stream().collect(Collectors.toMap(
                    ValidationCheck::getLocation,
                    Function.identity(),
                    (a, b) -> {
                        if (a.getStatus() == ValidationCheck.Status.ERROR) {
                            return a;
                        }
                        if (b.getStatus() == ValidationCheck.Status.ERROR) {
                            return b;
                        }
                        // if both of them are warnings, it doesn't matter
                        return a;
                    }
            ));

            return vr.getValidatedObjects().stream().map(r -> {
                Optional<ValidationCheck> check = r.getLocations().stream().map(checkMap::get).filter(Objects::nonNull).findFirst();
                return Pair.of(r, check);
            });
        }).sorted(
                Comparator.comparing(o -> location(o.getLeft()))
        ).map(pair ->
                mapRpkiObject(pair.getLeft(), pair.getRight().map(c -> ValidationResult.withLocation(c.getLocation())))
        ).filter(Objects::nonNull);

        return ResponseEntity.ok(ApiResponse.data(rpkiObjStream));
    }

    @GetMapping(path = "/certified.csv", produces = "text/csv; charset=UTF-8")
    public void certified(HttpServletResponse response) throws IOException {
        final IpResourceSet all = IpResourceSet.parse("0/0, ::/0");

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

        bottomLayer.parallelStream()
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
                .forEachOrdered(c -> {
                    IpResourceSet resources = c.getResources();
                    if (!resources.contains(all)) {
                        for (IpResource r : resources) {
                            if (r.getType() != IpResourceType.ASN) {
                                ipResources.add(r);
                            }
                        }
                    }
                });

        response.setContentType("text/csv; charset=UTF-8");
        try (final CSVWriter writer = new CSVWriter(response.getWriter())) {
            writer.writeNext(new String[]{"Resource"});
            for (IpResource r : ipResources) {
                writer.writeNext(new String[]{r.toString()});
            }
        }
    }

    private static <T> Set<T> oneElem(T c) {
        Set<T> a = new HashSet<>();
        a.add(c);
        return a;
    }

    private RpkiObj mapRpkiObject(final RpkiObject rpkiObject, final Optional<ValidationResult> validationResult) {
        switch (rpkiObject.getType()) {
            case CER:
                return makeTypedDto(rpkiObject, validationResult, X509ResourceCertificate.class, cert -> makeCertificate(validationResult, cert, rpkiObject));
            case ROUTER_CER:
                return makeTypedDto(rpkiObject, validationResult, X509RouterCertificate.class, cert -> makeCertificate(validationResult, cert, rpkiObject));
            case ROA:
                return makeTypedDto(rpkiObject, validationResult, RoaCms.class, cert -> makeRoa(validationResult, cert, rpkiObject));
            case MFT:
                return makeTypedDto(rpkiObject, validationResult, ManifestCms.class, cert -> makeMft(validationResult, cert, rpkiObject));
            case CRL:
                return makeTypedDto(rpkiObject, validationResult, X509Crl.class, cert -> makeCrl(validationResult, cert, rpkiObject));
            case GBR:
                return makeTypedDto(rpkiObject, validationResult, GhostbustersCms.class, gbr -> makeGbr(validationResult, gbr, rpkiObject));
            case OTHER:
                return makeOther(validationResult, rpkiObject);
        }
        throw new IllegalArgumentException("RPKI object with unknown type " + rpkiObject);
    }

    private <T extends CertificateRepositoryObject> RpkiObj makeTypedDto(final RpkiObject rpkiObject,
                                                                         final Optional<ValidationResult> validationResult,
                                                                         final Class<T> clazz,
                                                                         final Function<T, RpkiObj> create) {
        return validationResult
                .flatMap(vr -> rpkiObjects.findCertificateRepositoryObject(rpkiObject.getId(), clazz, vr))
                .map(create).orElse(null);
    }

    private static String location(final RpkiObject rpkiObject) {
        final SortedSet<String> locations = rpkiObject.getLocations();
        if (locations.isEmpty()) {
            return "unknown." + rpkiObject.getType().toString().toLowerCase(Locale.ROOT);
        }
        return locations.first();
    }

    private ResourceCertificate makeCertificate(final Optional<ValidationResult> validationResult, final X509ResourceCertificate certificate, final RpkiObject rpkiObject) {
        final String location = validationResult.map(vr -> vr.getCurrentLocation().getName()).orElse(location(rpkiObject));
        final boolean isValid = isValid(validationResult);

        return ResourceCertificate.builder().
                uri(location).
                valid(isValid).
                warnings(formatChecks(validationResult.map(ValidationResult::getWarnings).orElse(Collections.emptyList()))).
                errors(formatChecks(validationResult.map(ValidationResult::getFailuresForAllLocations).orElse(Collections.emptyList()))).
                resources(formatResources(certificate.getResources())).
                subjectName(certificate.getSubject().getName()).
                ski(Hex.format(certificate.getSubjectKeyIdentifier())).
                aki(Hex.format(certificate.getAuthorityKeyIdentifier())).
                validityTime(formatValidity(certificate.getValidityPeriod())).
                sia(formatSia(certificate.getSubjectInformationAccess())).
                serial(certificate.getSerialNumber()).
                sha256(Hex.format(rpkiObject.getSha256())).
                build();
    }

    private RouterCertificate makeCertificate(final Optional<ValidationResult> validationResult, final X509RouterCertificate certificate, final RpkiObject rpkiObject) {
        final String location = validationResult.map(vr -> vr.getCurrentLocation().getName()).orElse(location(rpkiObject));
        final boolean isValid = isValid(validationResult);

        return RouterCertificate.builder().
                uri(location).
                valid(isValid).
                warnings(formatChecks(validationResult.map(ValidationResult::getWarnings).orElse(Collections.emptyList()))).
                errors(formatChecks(validationResult.map(ValidationResult::getFailuresForAllLocations).orElse(Collections.emptyList()))).
                subjectName(certificate.getSubject().getName()).
                ski(Hex.format(certificate.getSubjectKeyIdentifier())).
                aki(Hex.format(certificate.getAuthorityKeyIdentifier())).
                validityTime(formatValidity(certificate.getValidityPeriod())).
                sia(formatSia(certificate.getSubjectInformationAccess())).
                serial(certificate.getSerialNumber()).
                sha256(Hex.format(rpkiObject.getSha256())).
                build();
    }

    private boolean isValid(Optional<ValidationResult> validationResult) {
        return !validationResult.filter(ValidationResult::hasFailureForCurrentLocation).isPresent();
    }

    private Roa makeRoa(final Optional<ValidationResult> validationResult, RoaCms roaCms, final RpkiObject rpkiObject) {
        final String location = validationResult.map(vr -> vr.getCurrentLocation().getName()).orElse(location(rpkiObject));
        final boolean isValid = isValid(validationResult);

        final List<net.ripe.rpki.commons.crypto.cms.roa.RoaPrefix> pref = roaCms.getPrefixes();
        final List<RoaPrefix> prefixes = pref == null ? Collections.emptyList() : pref.stream().map(p ->
                RoaPrefix.builder().
                        prefix(p.getPrefix().toString()).
                        maxLenght(p.getMaximumLength()).
                        build()
        ).collect(Collectors.toList());

        return Roa.builder().
                uri(location).
                valid(isValid).
                warnings(formatChecks(validationResult.map(ValidationResult::getWarnings).orElse(Collections.emptyList()))).
                errors(formatChecks(validationResult.map(ValidationResult::getFailuresForAllLocations).orElse(Collections.emptyList()))).
                asn(roaCms.getAsn().toString()).
                roaPrefixes(prefixes).
                sha256(Hex.format(rpkiObject.getSha256())).
                eeCertificate(makeEeCertificate(roaCms.getCertificate())).
                build();
    }

    private EeCertificate makeEeCertificate(X509ResourceCertificate certificate) {
        return EeCertificate.builder().
                validityTime(formatValidity(certificate.getValidityPeriod())).
                subjectName(certificate.getSubject().getName()).
                serial(certificate.getSerialNumber()).
                resources(formatResources(certificate.getResources())).
                build();
    }

    private Mft makeMft(final Optional<ValidationResult> validationResult, ManifestCms manifestCms, final RpkiObject rpkiObject) {
        final String location = validationResult.map(vr -> vr.getCurrentLocation().getName()).orElse(location(rpkiObject));
        final boolean isValid = isValid(validationResult);

        return Mft.builder().
                uri(location).
                valid(isValid).
                warnings(formatChecks(validationResult.map(ValidationResult::getWarnings).orElse(Collections.emptyList()))).
                errors(formatChecks(validationResult.map(ValidationResult::getFailuresForAllLocations).orElse(Collections.emptyList()))).
                eeCertificate(makeEeCertificate(manifestCms.getCertificate())).
                thisUpdateTime(manifestCms.getThisUpdateTime().toDate()).
                nextUpdateTime(manifestCms.getNextUpdateTime().toDate()).
                manifestNumber(manifestCms.getNumber()).
                entries(makeMftEntries(manifestCms.getFiles())).
                sha256(Hex.format(rpkiObject.getSha256())).
                build();
    }

    private List<MftEntry> makeMftEntries(final Map<String, byte[]> files) {
        return files.entrySet().stream().map(e ->
                MftEntry.builder().filename(e.getKey()).sha256(Hex.format(e.getValue())).build()
        ).collect(Collectors.toList());
    }

    private Crl makeCrl(final Optional<ValidationResult> validationResult, final X509Crl crl, final RpkiObject rpkiObject) {
        final String location = validationResult.map(vr -> vr.getCurrentLocation().getName()).orElse(location(rpkiObject));
        final boolean isValid = isValid(validationResult);

        final Set<? extends X509CRLEntry> revokedCertificates = crl.getCrl().getRevokedCertificates();
        final List<BigInteger> revocations = revokedCertificates == null ?
                Collections.emptyList() :
                revokedCertificates.stream().map(X509CRLEntry::getSerialNumber).collect(Collectors.toList());

        return Crl.builder().
                uri(location).
                valid(isValid).
                warnings(formatChecks(validationResult.map(ValidationResult::getWarnings).orElse(Collections.emptyList()))).
                errors(formatChecks(validationResult.map(ValidationResult::getFailuresForAllLocations).orElse(Collections.emptyList()))).
                aki(Hex.format(crl.getAuthorityKeyIdentifier())).
                serial(crl.getNumber()).
                revocations(revocations).
                build();
    }

    private RpkiObj makeGbr(Optional<ValidationResult> validationResult, GhostbustersCms gbr, RpkiObject rpkiObject) {
        final String location = validationResult.map(vr -> vr.getCurrentLocation().getName()).orElse(location(rpkiObject));
        final boolean isValid = isValid(validationResult);

        return GhostbustersRecord.builder()
            .uri(location)
            .valid(isValid)
            .warnings(formatChecks(validationResult.map(ValidationResult::getWarnings).orElse(Collections.emptyList())))
            .errors(formatChecks(validationResult.map(ValidationResult::getFailuresForAllLocations).orElse(Collections.emptyList())))
            .sha256(Hex.format(rpkiObject.getSha256()))
            .vCard(gbr.getVCardContent())
            .eeCertificate(makeEeCertificate(gbr.getCertificate()))
            .build();
    }

    private RpkiObj makeOther(final Optional<ValidationResult> vr, final RpkiObject ro) {
        return Other.builder().uri(location(ro)).build();
    }

    private static List<String> formatResources(final IpResourceSet resources) {
        return StreamSupport.stream(resources.spliterator(), false).map(Object::toString).collect(Collectors.toList());
    }

    private static Map<String, String> formatSia(final X509CertificateInformationAccessDescriptor[] sia) {
        return Arrays.stream(sia).collect(Collectors.toMap(
                s -> s.getMethod().toString(),
                s -> s.getLocation().toASCIIString()
        ));
    }

    private static ValidityTime formatValidity(final ValidityPeriod validityPeriod) {
        return ValidityTime.builder().
                notValidAfter(validityPeriod.getNotValidAfter().toDate()).
                notValidBefore(validityPeriod.getNotValidBefore().toDate()).
                build();
    }

    private static List<Issue> formatChecks(final List<net.ripe.rpki.commons.validation.ValidationCheck> checks) {
        return checks.stream().map(validationCheck -> Issue.builder().
                status(validationCheck.getStatus().toString()).
                key(validationCheck.getKey()).
                parameters(validationCheck.getParams()).
                build()).collect(Collectors.toList());
    }

    // @mpuzanov FIXME make it work and show some meaningful documentation
    @JsonTypeInfo(
            use = JsonTypeInfo.Id.NAME,
            include = JsonTypeInfo.As.PROPERTY,
            property = "type",
            visible = true
    )
    @JsonSubTypes({
            @JsonSubTypes.Type(value = ResourceCertificate.class, name = "certificate"),
            @JsonSubTypes.Type(value = Roa.class, name = "roa"),
            @JsonSubTypes.Type(value = Mft.class, name = "mft"),
            @JsonSubTypes.Type(value = Crl.class, name = "crl")
    })
    @ApiModel(
            value = "RpkiObj",
            subTypes = {ResourceCertificate.class, Roa.class, Mft.class, Crl.class},
            discriminator = "type"
    )
    static class RpkiObj {
        @ApiModelProperty(value = "the discriminator field.")
        protected String type;
    }

    @Builder
    @Getter
    @ApiModel(value = "Other", parent = RpkiObj.class)
    private static class Other extends RpkiObj {
        private String uri;

        public Other(String uri) {
            this.uri = uri;
            this.type = "other";
        }
    }

    @Builder
    @Getter
    private static class ValidityTime {
        private Date notValidBefore;
        private Date notValidAfter;
    }

    @Builder
    @Getter
    @ApiModel(value = "Certificate", parent = RpkiObj.class)
    private static class ResourceCertificate extends RpkiObj {
        private String uri;
        private boolean valid;
        private List<Issue> warnings;
        private List<Issue> errors;

        private List<String> resources;
        private String subjectName;
        private String ski;
        private String aki;
        private ValidityTime validityTime;
        private Map<String, String> sia;
        // TODO There're three serials in the story
        private BigInteger serial;
        private String sha256;
    }

    @Builder
    @Getter
    @ApiModel(value = "Router Certificate", parent = RpkiObj.class)
    private static class RouterCertificate extends RpkiObj {
        private String uri;
        private boolean valid;
        private List<Issue> warnings;
        private List<Issue> errors;

        private String subjectName;
        private String ski;
        private String aki;
        private ValidityTime validityTime;
        private Map<String, String> sia;
        private BigInteger serial;
        private String sha256;
    }

    @Builder
    @Getter
    static class EeCertificate {
        private List<String> resources;
        private String subjectName;
        private ValidityTime validityTime;
        private BigInteger serial;
    }

    @Builder
    @Getter
    static class RoaPrefix {
        private String prefix;
        private Integer maxLenght;
    }

    @Builder
    @Getter
    @ApiModel(value = "Roa", parent = RpkiObj.class)
    private static class Roa extends RpkiObj {
        private String uri;
        private boolean valid;
        private List<Issue> warnings;
        private List<Issue> errors;

        private String asn;
        private EeCertificate eeCertificate;
        private List<RoaPrefix> roaPrefixes;
        private String sha256;
    }

    @Builder
    @Getter
    @ApiModel(value = "Mft", parent = RpkiObj.class)
    static class Mft extends RpkiObj {
        private String uri;
        private boolean valid;
        private List<Issue> warnings;
        private List<Issue> errors;

        private EeCertificate eeCertificate;
        private Date thisUpdateTime;
        private Date nextUpdateTime;
        private BigInteger manifestNumber;
        private List<MftEntry> entries;
        private String sha256;
    }

    @Builder
    @Getter
    static class MftEntry {
        private String sha256;
        private String filename;
    }

    @Builder
    @Getter
    @ApiModel(value = "Crl", parent = RpkiObj.class)
    static class Crl extends RpkiObj {
        private String uri;
        private boolean valid;
        private List<Issue> warnings;
        private List<Issue> errors;

        private String aki;
        private List<BigInteger> revocations;
        private BigInteger serial;
    }

    @Builder
    @Getter
    static class Issue {
        private String status;
        private String key;
        private String[] parameters;
    }

    @Builder
    @Getter
    @ApiModel(value = "GhostbustersRecord", parent = RpkiObj.class)
    private static class GhostbustersRecord extends RpkiObj {
        private String uri;
        private boolean valid;
        private List<Issue> warnings;
        private List<Issue> errors;

        private String vCard;
        private EeCertificate eeCertificate;
        private String sha256;
    }

    /*
     TODO
        Add Router Certificate
    */

}
