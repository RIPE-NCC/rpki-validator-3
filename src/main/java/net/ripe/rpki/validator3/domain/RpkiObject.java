package net.ripe.rpki.validator3.domain;

import lombok.Getter;
import net.ripe.rpki.commons.crypto.CertificateRepositoryObject;
import net.ripe.rpki.commons.crypto.util.CertificateRepositoryObjectFactory;
import net.ripe.rpki.commons.validation.ValidationResult;
import net.ripe.rpki.validator3.domain.constraints.ValidLocationURI;

import javax.persistence.*;
import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity
public class RpkiObject extends AbstractEntity {

    public static final int MAX_SIZE = 1024 * 1024;

    @ElementCollection(fetch = FetchType.EAGER)
    @OrderColumn
    @Getter
    @NotNull
    @NotEmpty
    @Size(max = 1)
    @Valid
    private List<@NotNull @ValidLocationURI String> locations;

    @Basic
    @Getter
    private BigInteger serialNumber;

    @Basic
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

    public RpkiObject(List<@NotNull @ValidLocationURI String> locations, BigInteger serialNumber, byte[] sha256, byte[] encoded) {
        this.locations = new ArrayList<>(locations);
        this.serialNumber = Objects.requireNonNull(serialNumber, "serialNumber is required");
        this.sha256 = Objects.requireNonNull(sha256, "sha256 is required");
        this.encoded = Objects.requireNonNull(encoded, "encoded is required");
    }

    public <T extends CertificateRepositoryObject> T get(Class<T> expectedType) {
        ValidationResult validationResult = ValidationResult.withLocation(locations.get(0));
        CertificateRepositoryObject parsed = CertificateRepositoryObjectFactory.createCertificateRepositoryObject(encoded, validationResult);
        return expectedType.cast(parsed);
    }
}
