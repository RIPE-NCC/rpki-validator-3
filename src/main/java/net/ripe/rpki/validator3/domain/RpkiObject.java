package net.ripe.rpki.validator3.domain;

import lombok.Getter;
import net.ripe.rpki.commons.crypto.CertificateRepositoryObject;
import net.ripe.rpki.commons.crypto.UnknownCertificateRepositoryObject;
import net.ripe.rpki.commons.crypto.cms.RpkiSignedObject;
import net.ripe.rpki.commons.crypto.cms.manifest.ManifestCms;
import net.ripe.rpki.commons.crypto.cms.roa.RoaCms;
import net.ripe.rpki.commons.crypto.crl.X509Crl;
import net.ripe.rpki.commons.crypto.util.CertificateRepositoryObjectFactory;
import net.ripe.rpki.commons.crypto.x509cert.X509ResourceCertificate;
import net.ripe.rpki.commons.validation.ValidationResult;
import net.ripe.rpki.validator3.domain.constraints.ValidLocationURI;
import net.ripe.rpki.validator3.util.Sha256;
import org.apache.commons.lang3.tuple.Pair;
import org.bouncycastle.util.encoders.Hex;

import javax.persistence.*;
import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.math.BigInteger;
import java.net.URI;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Entity
public class RpkiObject extends AbstractEntity {

    public static final int MAX_SIZE = 1024 * 1024;

    public enum Type {
        CER, MFT, CRL, ROA, OTHER
    }

    @Basic(optional = false)
    @Enumerated(EnumType.STRING)
    @NotNull
    @Getter
    private Type type;

    @ElementCollection(fetch = FetchType.LAZY)
    @OrderBy("locations")
    @Getter
    @NotNull
    @Size(max = 1)
    @Valid
    private SortedSet<@NotNull @ValidLocationURI String> locations = new TreeSet<>();

    @Basic
    @Getter
    private BigInteger serialNumber;

    @Basic
    @Getter
    private Instant signingTime;

    @Basic
    @Getter
    private byte[] authorityKeyIdentifier;

    @Basic(optional = false)
    @NotNull
    @Getter
    private byte[] sha256;

    @Basic
    @Getter
    @NotNull
    @NotEmpty
    @Size(max = MAX_SIZE)
    private byte[] encoded;

    @ElementCollection(fetch = FetchType.LAZY)
    @OrderColumn
    @NotNull
    @Valid
    @Getter
    private List<RoaPrefix> roaPrefixes = new ArrayList<>();

    protected RpkiObject() {
        super();
    }

    public RpkiObject(URI location, CertificateRepositoryObject object) {
        this(location.toASCIIString(), object);
    }

    public RpkiObject(String location, CertificateRepositoryObject object) {
        this.locations.add(location);
        this.encoded = object.getEncoded();
        this.sha256 = Sha256.hash(this.encoded);
        if (object instanceof X509ResourceCertificate) {
            this.serialNumber = ((X509ResourceCertificate) object).getSerialNumber();
            this.signingTime = null; // Use not valid before instead?
            this.authorityKeyIdentifier = ((X509ResourceCertificate) object).getAuthorityKeyIdentifier();
            this.type = Type.CER; // FIXME separate certificate types? CA, EE, Router, ?
        } else if (object instanceof X509Crl) {
            this.serialNumber = ((X509Crl) object).getNumber();
            this.signingTime = Instant.ofEpochMilli(((X509Crl) object).getThisUpdateTime().getMillis());
            this.authorityKeyIdentifier = ((X509Crl) object).getAuthorityKeyIdentifier();
            this.type = Type.CRL;
        } else if (object instanceof RpkiSignedObject) {
            this.serialNumber = ((RpkiSignedObject) object).getCertificate().getSerialNumber();
            this.signingTime = Instant.ofEpochMilli(((RpkiSignedObject) object).getSigningTime().getMillis());
            this.authorityKeyIdentifier = ((RpkiSignedObject) object).getCertificate().getAuthorityKeyIdentifier();
            this.type = (
                (object instanceof ManifestCms) ? Type.MFT
                    : (object instanceof RoaCms) ? Type.ROA
                    : Type.OTHER
            );
            if (object instanceof RoaCms) {
                RoaCms roaCms = (RoaCms) object;
                this.roaPrefixes = roaCms.getPrefixes().stream()
                    .map(prefix -> RoaPrefix.of(prefix.getPrefix(), prefix.getMaximumLength(), roaCms.getAsn()))
                    .collect(Collectors.toList());
            }
        } else if (object instanceof UnknownCertificateRepositoryObject) {
            // FIXME store these at all?
            throw new IllegalArgumentException("unsupported certificate repository object type " + object);
        } else {
            throw new IllegalArgumentException("unsupported certificate repository object type " + object);
        }
    }

    public <T extends CertificateRepositoryObject> Optional<T> get(Class<T> clazz, ValidationResult validationResult) {
        ValidationResult temporary = ValidationResult.withLocation(validationResult.getCurrentLocation());
        try {
            return get(clazz, validationResult.getCurrentLocation().getName());
        } finally {
            validationResult.addAll(temporary);
        }
    }

    public <T extends CertificateRepositoryObject> Optional<T> get(final Class<T> clazz, final String location) {
        ValidationResult temporary = ValidationResult.withLocation(location);

        temporary.rejectIfFalse(Arrays.equals(Sha256.hash(encoded), sha256), "rpki.object.sha256.matches");
        if (temporary.hasFailureForCurrentLocation()) {
            return Optional.empty();
        }

        ValidationResult ignored = ValidationResult.withLocation(location);
        CertificateRepositoryObject candidate = CertificateRepositoryObjectFactory.createCertificateRepositoryObject(
                encoded,
                ignored // Ignore any parse errors, as all stored objects must be parsable
        );

        temporary.rejectIfNull(candidate, "rpki.object.parsable");
        if (temporary.hasFailureForCurrentLocation()) {
            return Optional.empty();
        }

        temporary.rejectIfFalse(clazz.isInstance(candidate), "rpki.object.type.matches", clazz.getSimpleName(), candidate.getClass().getSimpleName());
        if (temporary.hasFailureForCurrentLocation()) {
            return Optional.empty();
        }

        return Optional.of(clazz.cast(candidate));
    }

    public void addLocation(String location) {
        this.locations.add(location);
    }

    public void removeLocation(String location) {
        this.locations.remove(location);
    }

    @Override
    public String toString() {
        return toStringBuilder()
                .append("type", getType())
                .append("hash", Hex.toHexString(getSha256()))
                .append("serialNumber", getSerialNumber())
                .append("locations", getLocations())
                .build();
    }
}
