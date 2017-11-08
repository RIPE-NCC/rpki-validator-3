package net.ripe.rpki.validator3.api.rpkiobjects;

import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.validator3.api.Api;
import net.ripe.rpki.validator3.api.ApiResponse;
import net.ripe.rpki.validator3.domain.CertificateTreeValidationRun;
import net.ripe.rpki.validator3.domain.RpkiObject;
import net.ripe.rpki.validator3.domain.RpkiObjects;
import net.ripe.rpki.validator3.domain.ValidationRuns;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.constraints.NotNull;
import java.math.BigInteger;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

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
        vrs.stream().map(vr -> {
            final Set<RpkiObject> validatedObjects = vr.getValidatedObjects();
            return validatedObjects;
        });


        final Stream<RpkiObj> rpkiObjStream = rpkiObjects.findCurrentlyValidated().map(p -> {
            final CertificateTreeValidationRun vr = p.getLeft();
            final RpkiObject ro = p.getRight();
            switch (ro.getType()) {
                case CER:
                    return makeCertificate(vr, ro);
                case ROA:
                    return makeRoa(vr, ro);
                case MFT:
                    return makeMft(vr, ro);
                case CRL:
                    return makeCrl(vr, ro);
                default:
                    return makeSomething(vr, ro);
            }
        });
        return ResponseEntity.ok(ApiResponse.data(rpkiObjStream));
    }

    private ResourceCertificate makeCertificate(CertificateTreeValidationRun vr, RpkiObject ro) {
        return ResourceCertificate.builder().build();
    }

    private Roa makeRoa(CertificateTreeValidationRun vr, RpkiObject ro) {
        return Roa.builder().build();
    }

    private Mft makeMft(CertificateTreeValidationRun vr, RpkiObject ro) {
        return Mft.builder().build();
    }

    private Crl makeCrl(CertificateTreeValidationRun vr, RpkiObject ro) {
        return Crl.builder().build();
    }

    private RpkiObj makeSomething(CertificateTreeValidationRun vr, RpkiObject ro) {
        return new RpkiObj();
    }

    static class RpkiObj {
    }

    @Builder
    private static class ResourceCertificate extends RpkiObj {
        private String uri;
        private boolean valid;
        private String[] warnings;
        private String[] errors;

        private String[] resources;
        private String subjectName;
        private String ski;
        private String aki;
        private String validityTime;
        private String sia;
        // TODO There're three serials in the story
        private BigInteger serial;
        private String sha256;
    }

    @Builder
    static class EeCertificate {
        private String[] resources;
        private String subjectName;
        private String validityTime;
        private BigInteger serial;
    }

    @Builder
    static class RoaPrefix {
        private String asn;
        private String prefix;
        private int maxLenght;
    }

    @Builder
    private static class Roa extends RpkiObj {
        private String uri;
        private boolean valid;
        private String[] warnings;
        private String[] errors;

        private EeCertificate eeCertificate;
        private RoaPrefix roaPrefix;
        private String sha256;
    }

    @Builder
    static class Mft extends RpkiObj {
        private String uri;
        private boolean valid;
        private String[] warnings;
        private String[] errors;

        private EeCertificate eeCertificate;
        private String thisUpdateTime;
        private String nextUpdateTime;
        private int manifestNumber;
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
        private String[] warnings;
        private String[] errors;

        private String aki;
        private String revocations;
        private BigInteger serial;
    }

    /*
     TODO
        Add GBR
        Add Router Certificate
    */

}
