package net.ripe.rpki.validator3.domain;

import lombok.Getter;
import net.ripe.rpki.validator3.domain.constraints.ValidLocationURI;

import javax.persistence.*;
import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.math.BigInteger;
import java.util.Objects;
import java.util.SortedSet;
import java.util.TreeSet;

@Entity
public class RpkiObject extends AbstractEntity {

    public static final int MAX_SIZE = 1024 * 1024;

    @ManyToOne(optional = false)
    @Getter
    @NotNull
    @Valid
    private RpkiRepository rpkiRepository;

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

    public RpkiObject(RpkiRepository rpkiRepository, BigInteger serialNumber, byte[] sha256, byte[] encoded) {
        this.rpkiRepository = rpkiRepository;
        this.serialNumber = Objects.requireNonNull(serialNumber, "serialNumber is required");
        this.sha256 = Objects.requireNonNull(sha256, "sha256 is required");
        this.encoded = Objects.requireNonNull(encoded, "encoded is required");
    }

    public void addLocation(String location) {
        this.locations.add(location);
    }
}
