package net.ripe.rpki.validator3.domain;

import lombok.Getter;
import lombok.Setter;
import net.ripe.rpki.commons.crypto.util.CertificateRepositoryObjectFactory;
import net.ripe.rpki.commons.crypto.x509cert.X509ResourceCertificate;
import net.ripe.rpki.commons.validation.ValidationResult;
import net.ripe.rpki.validator3.domain.constraints.ValidLocationURI;
import net.ripe.rpki.validator3.domain.constraints.ValidPublicKeyInfo;

import javax.persistence.*;
import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.ArrayList;
import java.util.List;

@Entity
public class TrustAnchor extends AbstractEntity {

    public static final String TYPE = "trust-anchor";

    @Basic
    @Getter
    @Setter
    @NotNull
    @Size(min = 1, max = 1000)
    private String name;

    @ElementCollection(fetch = FetchType.EAGER)
    @OrderColumn
    @Getter
    @Setter
    @NotNull
    @NotEmpty
    @Size(max = 1)
    @Valid
    private List<@NotNull @ValidLocationURI String> locations = new ArrayList<>();

    @Basic
    @Getter
    @Setter
    @NotNull
    @ValidPublicKeyInfo
    private String subjectPublicKeyInfo;

    @Basic
    @Getter
    @Setter
    @ValidLocationURI
    private String rsyncPrefetchUri;

    @Basic
    @Size(max = RpkiObject.MAX_SIZE)
    @Getter
    private byte[] encodedCertificate;

    public void setCertificate(X509ResourceCertificate certificate) {
        this.encodedCertificate = certificate.getEncoded();
    }

    public X509ResourceCertificate getCertificate() {
        if (encodedCertificate == null) {
            return null;
        }

        return (X509ResourceCertificate) CertificateRepositoryObjectFactory.createCertificateRepositoryObject(
            encodedCertificate,
            ValidationResult.withLocation(locations.get(0))
        );
    }

    @Override
    public String toString() {
        return toStringBuilder()
            .append("name", getName())
            .append("locations", getLocations())
            .build();
    }
}
