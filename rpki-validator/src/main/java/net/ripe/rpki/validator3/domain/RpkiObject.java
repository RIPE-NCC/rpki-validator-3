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

import lombok.Getter;
import net.ripe.rpki.commons.crypto.CertificateRepositoryObject;
import net.ripe.rpki.commons.crypto.UnknownCertificateRepositoryObject;
import net.ripe.rpki.commons.crypto.cms.RpkiSignedObject;
import net.ripe.rpki.commons.crypto.cms.ghostbuster.GhostbustersCms;
import net.ripe.rpki.commons.crypto.cms.manifest.ManifestCms;
import net.ripe.rpki.commons.crypto.cms.roa.RoaCms;
import net.ripe.rpki.commons.crypto.crl.X509Crl;
import net.ripe.rpki.commons.crypto.util.CertificateRepositoryObjectFactory;
import net.ripe.rpki.commons.crypto.x509cert.X509ResourceCertificate;
import net.ripe.rpki.commons.crypto.x509cert.X509RouterCertificate;
import net.ripe.rpki.commons.validation.ValidationResult;
import net.ripe.rpki.validator3.domain.constraints.ValidLocationURI;
import net.ripe.rpki.validator3.util.Sha256;
import org.bouncycastle.util.encoders.Hex;

import javax.persistence.Basic;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.FetchType;
import javax.persistence.OrderBy;
import javax.persistence.OrderColumn;
import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.math.BigInteger;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;

@Entity
public class RpkiObject extends AbstractEntity {

    public static final int MAX_SIZE = 1024 * 1024;

    public enum Type {
        CER, MFT, CRL, ROA, GBR, ROUTER_CER, OTHER
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

    @Basic(optional = false)
    @Getter
    @NotNull
    private Instant lastMarkedReachableAt;

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
        this.lastMarkedReachableAt = Instant.now();
        if (object instanceof X509ResourceCertificate) {
            this.serialNumber = ((X509ResourceCertificate) object).getSerialNumber();
            this.signingTime = null; // Use not valid before instead?
            this.authorityKeyIdentifier = ((X509ResourceCertificate) object).getAuthorityKeyIdentifier();
            this.type = Type.CER; // FIXME separate certificate types? CA, EE, Router, ?
        } else  if (object instanceof X509RouterCertificate) {
            this.serialNumber = ((X509RouterCertificate) object).getSerialNumber();
            this.signingTime = null;
            this.authorityKeyIdentifier = ((X509RouterCertificate) object).getAuthorityKeyIdentifier();
            this.type = Type.ROUTER_CER;
        } else if (object instanceof X509Crl) {
            this.serialNumber = ((X509Crl) object).getNumber();
            this.signingTime = Instant.ofEpochMilli(((X509Crl) object).getThisUpdateTime().getMillis());
            this.authorityKeyIdentifier = ((X509Crl) object).getAuthorityKeyIdentifier();
            this.type = Type.CRL;
        } else if (object instanceof RpkiSignedObject) {
            this.serialNumber = ((RpkiSignedObject) object).getCertificate().getSerialNumber();
            this.signingTime = Instant.ofEpochMilli(((RpkiSignedObject) object).getSigningTime().getMillis());
            this.authorityKeyIdentifier = ((RpkiSignedObject) object).getCertificate().getAuthorityKeyIdentifier();
            if (object instanceof ManifestCms) {
                this.type = Type.MFT;
            } else if (object instanceof RoaCms) {
                RoaCms roaCms = (RoaCms) object;
                this.type = Type.ROA;
                this.roaPrefixes = roaCms.getPrefixes().stream()
                    .map(prefix -> RoaPrefix.of(prefix.getPrefix(), prefix.getMaximumLength(), roaCms.getAsn()))
                    .collect(Collectors.toList());
            } else if (object instanceof GhostbustersCms) {
                this.type = Type.GBR;
            } else {
                this.type = Type.OTHER;
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

    /**
     * Mark this object as currently reachable by following a chain of references from the
     * trust anchors through manifest to this object.
     */
    public void markReachable(Instant now) {
        this.lastMarkedReachableAt = now;
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
