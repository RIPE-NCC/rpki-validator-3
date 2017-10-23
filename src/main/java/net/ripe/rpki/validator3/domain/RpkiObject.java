package net.ripe.rpki.validator3.domain;

import lombok.Getter;
import net.ripe.rpki.commons.crypto.CertificateRepositoryObject;
import net.ripe.rpki.commons.crypto.UnknownCertificateRepositoryObject;
import net.ripe.rpki.commons.crypto.cms.RpkiSignedObject;
import net.ripe.rpki.commons.crypto.cms.manifest.ManifestCms;
import net.ripe.rpki.commons.crypto.crl.X509Crl;
import net.ripe.rpki.commons.crypto.x509cert.X509ResourceCertificate;
import net.ripe.rpki.validator3.domain.constraints.ValidLocationURI;
import net.ripe.rpki.validator3.util.Sha256;

import javax.persistence.*;
import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.io.IOException;
import java.math.BigInteger;
import java.time.Instant;
import java.util.SortedSet;
import java.util.TreeSet;

@Entity
public class RpkiObject extends AbstractEntity {

    public static final int MAX_SIZE = 1024 * 1024;

    public enum Type {
        MFT, CRL, OTHER
    }

    @Basic(optional = false)
    @Enumerated(EnumType.STRING)
    @NotNull
    @Getter
    private Type type;

    @ElementCollection(fetch = FetchType.EAGER)
    @OrderBy("locations")
    @Getter
    @NotNull
    @NotEmpty
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

    protected RpkiObject() {
        super();
    }

    public RpkiObject(CertificateRepositoryObject object) throws IOException {
        this.encoded = object.getEncoded();
        this.sha256 = Sha256.hash(this.encoded);
        if (object instanceof X509ResourceCertificate) {
            this.serialNumber = ((X509ResourceCertificate) object).getSerialNumber();
            this.signingTime = null; // Use not valid before instead?
            this.authorityKeyIdentifier = ((X509ResourceCertificate) object).getAuthorityKeyIdentifier();
            this.type = Type.OTHER;
        } else if (object instanceof X509Crl) {
            this.serialNumber = ((X509Crl) object).getNumber();
            this.signingTime = Instant.ofEpochMilli(((X509Crl) object).getThisUpdateTime().getMillis());
            this.authorityKeyIdentifier = ((X509Crl) object).getAuthorityKeyIdentifier();
            this.type = Type.CRL;
        } else if (object instanceof RpkiSignedObject) {
            this.serialNumber = ((RpkiSignedObject) object).getCertificate().getSerialNumber();
            this.signingTime = Instant.ofEpochMilli(((RpkiSignedObject) object).getSigningTime().getMillis());
            this.authorityKeyIdentifier = ((RpkiSignedObject) object).getCertificate().getAuthorityKeyIdentifier();
            this.type = (object instanceof ManifestCms) ? Type.MFT : Type.OTHER;
        } else if (object instanceof UnknownCertificateRepositoryObject) {
            // FIXME store these at all?
        } else {
            throw new IllegalArgumentException("unsupported certificate repository object type " + object);
        }
    }

    public void addLocation(String location) {
        this.locations.add(location);
    }
}
