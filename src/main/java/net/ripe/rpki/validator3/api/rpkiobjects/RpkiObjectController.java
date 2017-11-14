package net.ripe.rpki.validator3.api.rpkiobjects;

import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import net.ripe.ipresource.IpResourceSet;
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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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
        Stream<Set<RpkiObject>> setStream = vrs.stream().map(vr -> {
            List<ValidationCheck> validationChecks = vr.getValidationChecks();
            final Set<RpkiObject> validatedObjects = vr.getValidatedObjects();
            return validatedObjects;
        });


        final Stream<Pair<RpkiObject, ValidationResult>> objects = Stream.empty();

        final Stream<RpkiObj> rpkiObjStream = objects.map(p -> {
            final RpkiObject rpkiObject = p.getLeft();
            final ValidationResult validationResult = p.getRight();
            switch (rpkiObject.getType()) {
                case CER:
                    final Optional<X509ResourceCertificate> maybeCertificate = rpkiObject.get(X509ResourceCertificate.class, validationResult);
                    if (maybeCertificate.isPresent()) {
                        return makeCertificate(validationResult, maybeCertificate.get(), rpkiObject);
                    }
                    return null;
                case ROA:
                    final Optional<RoaCms> maybeRoa = rpkiObject.get(RoaCms.class, validationResult);
                    if (maybeRoa.isPresent()) {
                        return makeRoa(validationResult, maybeRoa.get(), rpkiObject);
                    }
                    return null;
                case MFT:
                    final Optional<ManifestCms> maybeMft = rpkiObject.get(ManifestCms.class, validationResult);
                    if (maybeMft.isPresent()) {
                        return makeMft(validationResult, maybeMft.get(), rpkiObject);
                    }
                    return null;
                case CRL:
                    final Optional<X509Crl> maybeCrl = rpkiObject.get(X509Crl.class, validationResult);
                    if (maybeCrl.isPresent()) {
                        return makeCrl(validationResult, maybeCrl.get(), rpkiObject);
                    }
                    return null;
                default:
                    return makeSomething(validationResult, rpkiObject);
            }
        }).filter(Objects::nonNull);

        return ResponseEntity.ok(ApiResponse.data(rpkiObjStream));
    }

    private ResourceCertificate makeCertificate(ValidationResult validationResult, X509ResourceCertificate certificate, RpkiObject rpkiObject) {
        return ResourceCertificate.builder().
                uri(validationResult.getCurrentLocation().getName()).
                valid(!validationResult.hasFailureForCurrentLocation()).
                warnings(formatChecks(validationResult.getWarnings())).
                errors(formatChecks(validationResult.getFailuresForAllLocations())).
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

    private Roa makeRoa(ValidationResult validationResult, RoaCms roaCms, RpkiObject rpkiObject) {
        return Roa.builder().
                uri(validationResult.getCurrentLocation().getName()).
                valid(!validationResult.hasFailureForCurrentLocation()).
                warnings(formatChecks(validationResult.getWarnings())).
                errors(formatChecks(validationResult.getFailuresForAllLocations())).
                asn(roaCms.getAsn().toString()).
                roaPrefixes(roaCms.getPrefixes().stream().map(p ->
                        RoaPrefix.builder().maxLenght(p.getMaximumLength()).build()
                ).collect(Collectors.toList())).
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

    private Mft makeMft(ValidationResult validationResult, ManifestCms manifestCms, RpkiObject rpkiObject) {
        return Mft.builder().
                uri(validationResult.getCurrentLocation().getName()).
                valid(!validationResult.hasFailureForCurrentLocation()).
                warnings(formatChecks(validationResult.getWarnings())).
                errors(formatChecks(validationResult.getFailuresForAllLocations())).
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

    private Crl makeCrl(ValidationResult validationResult, X509Crl crl, RpkiObject rpkiObject) {
        return Crl.builder().
                uri(validationResult.getCurrentLocation().getName()).
                valid(!validationResult.hasFailureForCurrentLocation()).
                warnings(formatChecks(validationResult.getWarnings())).
                errors(formatChecks(validationResult.getFailuresForAllLocations())).
                aki(Hex.format(crl.getAuthorityKeyIdentifier())).
                serial(crl.getNumber()).
                revocations(crl.getCrl().getRevokedCertificates().stream().map(X509CRLEntry::getSerialNumber).collect(Collectors.toList())).
                build();
    }

    private RpkiObj makeSomething(ValidationResult vr, RpkiObject ro) {
        return new RpkiObj();
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
    private static class ValidityTime extends RpkiObj {
        private String notValidBefore;
        private String notValidAfter;
    }

    @Builder
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
    static class EeCertificate {
        private List<String> resources;
        private String subjectName;
        private ValidityTime validityTime;
        private BigInteger serial;
    }

    @Builder
    static class RoaPrefix {
        private String prefix;
        private int maxLenght;
    }

    @Builder
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
    static class MftEntry {
        private String sha256;
        private String filename;
    }

    @Builder
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
