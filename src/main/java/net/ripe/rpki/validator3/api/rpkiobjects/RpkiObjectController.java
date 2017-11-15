package net.ripe.rpki.validator3.api.rpkiobjects;

import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.ripe.ipresource.IpResourceSet;
import net.ripe.rpki.commons.crypto.CertificateRepositoryObject;
import net.ripe.rpki.commons.crypto.ValidityPeriod;
import net.ripe.rpki.commons.crypto.cms.manifest.ManifestCms;
import net.ripe.rpki.commons.crypto.cms.roa.RoaCms;
import net.ripe.rpki.commons.crypto.crl.X509Crl;
import net.ripe.rpki.commons.crypto.x509cert.X509CertificateInformationAccessDescriptor;
import net.ripe.rpki.commons.crypto.x509cert.X509ResourceCertificate;
import net.ripe.rpki.commons.validation.ValidationResult;
import net.ripe.rpki.validator3.api.Api;
import net.ripe.rpki.validator3.api.ApiResponse;
import net.ripe.rpki.validator3.domain.CertificateTreeValidationRun;
import net.ripe.rpki.validator3.domain.RpkiObject;
import net.ripe.rpki.validator3.domain.RpkiObjects;
import net.ripe.rpki.validator3.domain.ValidationCheck;
import net.ripe.rpki.validator3.domain.ValidationRuns;
import net.ripe.rpki.validator3.util.Hex;
import org.apache.commons.lang3.tuple.Pair;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigInteger;
import java.security.cert.X509CRLEntry;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

@RestController
@RequestMapping(path = "/rpki-objects", produces = Api.API_MIME_TYPE)
@Slf4j
public class RpkiObjectController {
    private final RpkiObjects rpkiObjects;

    private final ValidationRuns validationRuns;

    @Autowired
    public RpkiObjectController(RpkiObjects rpkiObjects, ValidationRuns validationRuns) {
        this.rpkiObjects = rpkiObjects;
        this.validationRuns = validationRuns;
    }

    @GetMapping(path = "/all")
    public ResponseEntity<ApiResponse<Stream<RpkiObj>>> list() {
        final List<CertificateTreeValidationRun> vrs = validationRuns.findLatestSuccessful(CertificateTreeValidationRun.class);

        final Stream<RpkiObj> rpkiObjStream = vrs.stream().flatMap(vr -> {
            final Map<String, ValidationCheck> checkMap = vr.getValidationChecks().
                    stream().collect(Collectors.toMap(ValidationCheck::getLocation, Function.identity()));

            final Set<RpkiObject> validatedObjects = vr.getValidatedObjects();
            return validatedObjects.stream().map(r -> {
                Optional<ValidationCheck> check = r.getLocations().stream().map(checkMap::get).filter(Objects::nonNull).findFirst();
                return Pair.of(r, check);
            });
        }).sorted(Comparator.comparing(o -> location(o.getLeft()))).map(p -> {
                    final Optional<ValidationCheck> maybeCheck = p.getRight();
                    return mapRpkiObject(p.getLeft(), maybeCheck.map(c -> ValidationResult.withLocation(c.getLocation())));
                }
        ).filter(Objects::nonNull);

        return ResponseEntity.ok(ApiResponse.data(rpkiObjStream));
    }

    private RpkiObj mapRpkiObject(final RpkiObject rpkiObject, final Optional<ValidationResult> validationResult) {
        switch (rpkiObject.getType()) {
            case CER:
                return makeTypedObject(rpkiObject, validationResult, X509ResourceCertificate.class, cert -> makeCertificate(validationResult, cert, rpkiObject));
            case ROA:
                return makeTypedObject(rpkiObject, validationResult, RoaCms.class, cert -> makeRoa(validationResult, cert, rpkiObject));
            case MFT:
                return makeTypedObject(rpkiObject, validationResult, ManifestCms.class, cert -> makeMft(validationResult, cert, rpkiObject));
            case CRL:
                return makeTypedObject(rpkiObject, validationResult, X509Crl.class, cert -> makeCrl(validationResult, cert, rpkiObject));
            default:
                return makeOther(validationResult, rpkiObject);
        }
    }

    private <T extends CertificateRepositoryObject> RpkiObj makeTypedObject(final RpkiObject rpkiObject,
                                                                            final Optional<ValidationResult> validationResult,
                                                                            Class<T> clazz, Function<T, RpkiObj> create) {
        Optional<T> maybeCert = validationResult.flatMap(vr -> rpkiObject.get(clazz, vr));
        if (!maybeCert.isPresent()) {
            maybeCert = rpkiObject.get(clazz, location(rpkiObject));
        }
        if (maybeCert.isPresent()) {
            return create.apply(maybeCert.get());
        }
        return null;
    }

    private static String location(RpkiObject rpkiObject) {
        SortedSet<String> locations = rpkiObject.getLocations();
        if (locations.isEmpty()) {
            return "unknown." + rpkiObject.getType().toString().toLowerCase(Locale.ROOT);
        }
        return locations.first();
    }

    private ResourceCertificate makeCertificate(Optional<ValidationResult> validationResult, X509ResourceCertificate certificate, RpkiObject rpkiObject) {
        final String location = validationResult.map(vr -> vr.getCurrentLocation().getName()).orElse(rpkiObject.getLocations().first());
        final boolean isValid = validationResult.filter(vr -> !vr.hasFailureForCurrentLocation()).isPresent();

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

    private Roa makeRoa(Optional<ValidationResult> validationResult, RoaCms roaCms, RpkiObject rpkiObject) {
        final String location = validationResult.map(vr -> vr.getCurrentLocation().getName()).orElse(rpkiObject.getLocations().first());
        final boolean isValid = validationResult.filter(vr -> !vr.hasFailureForCurrentLocation()).isPresent();
        List<net.ripe.rpki.commons.crypto.cms.roa.RoaPrefix> pref = roaCms.getPrefixes();
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

    private Mft makeMft(Optional<ValidationResult> validationResult, ManifestCms manifestCms, RpkiObject rpkiObject) {
        final String location = validationResult.map(vr -> vr.getCurrentLocation().getName()).orElse(rpkiObject.getLocations().first());
        final boolean isValid = validationResult.filter(vr -> !vr.hasFailureForCurrentLocation()).isPresent();
        return Mft.builder().
                uri(location).
                valid(isValid).
                warnings(formatChecks(validationResult.map(ValidationResult::getWarnings).orElse(Collections.emptyList()))).
                errors(formatChecks(validationResult.map(ValidationResult::getFailuresForAllLocations).orElse(Collections.emptyList()))).
                eeCertificate(makeEeCertificate(manifestCms.getCertificate())).
                thisUpdateTime(formatDateTime(manifestCms.getThisUpdateTime())).
                nextUpdateTime(formatDateTime(manifestCms.getNextUpdateTime())).
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

    private Crl makeCrl(Optional<ValidationResult> validationResult, X509Crl crl, RpkiObject rpkiObject) {
        final String location = validationResult.map(vr -> vr.getCurrentLocation().getName()).orElse(rpkiObject.getLocations().first());
        final boolean isValid = validationResult.filter(vr -> !vr.hasFailureForCurrentLocation()).isPresent();
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

    private RpkiObj makeOther(Optional<ValidationResult> vr, RpkiObject ro) {
        return Other.builder().uri(ro.getLocations().first()).build();
    }

    private static List<String> formatResources(IpResourceSet resources) {
        return StreamSupport.stream(resources.spliterator(), false).map(Object::toString).collect(Collectors.toList());
    }

    private static Map<String, String> formatSia(X509CertificateInformationAccessDescriptor[] sia) {
        return Arrays.stream(sia).collect(Collectors.toMap(
                s -> s.getMethod().toString(),
                s -> s.getLocation().toASCIIString()
        ));
    }

    private static ValidityTime formatValidity(ValidityPeriod validityPeriod) {
        final DateTimeFormatter formatter = ISODateTimeFormat.basicDateTime();
        return ValidityTime.builder().
                notValidAfter(formatDateTime(validityPeriod.getNotValidAfter())).
                notValidBefore(formatDateTime(validityPeriod.getNotValidBefore())).
                build();
    }

    private static String formatDateTime(final DateTime dateTime) {
        return dateTime == null ? null : dateTime.toString(ISODateTimeFormat.basicDateTime());
    }

    private static List<Issue> formatChecks(final List<net.ripe.rpki.commons.validation.ValidationCheck> checks) {
        return checks.stream().map(validationCheck -> Issue.builder().
                status(validationCheck.getStatus().toString()).
                key(validationCheck.getKey()).
                parameters(validationCheck.getParams()).
                build()).collect(Collectors.toList());
    }

    static class RpkiObj {
    }

    @Builder
    @Getter
    private static class Other extends RpkiObj {
        private String uri;
    }

    @Builder
    @Getter
    private static class ValidityTime extends RpkiObj {
        private String notValidBefore;
        private String notValidAfter;
    }

    @Builder
    @Getter
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
    static class Mft extends RpkiObj {
        private String uri;
        private boolean valid;
        private List<Issue> warnings;
        private List<Issue> errors;

        private EeCertificate eeCertificate;
        private String thisUpdateTime;
        private String nextUpdateTime;
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

    /*
     TODO
        Add GBR
        Add Router Certificate
    */

}
