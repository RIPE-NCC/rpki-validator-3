package net.ripe.rpki.validator3.domain;

import lombok.Getter;
import lombok.Setter;
import net.ripe.rpki.validator3.domain.constraints.ValidLocationURI;
import net.ripe.rpki.validator3.domain.constraints.ValidPublicKeyInfo;

import javax.persistence.*;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
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
    @Size(min = 1, max = 1)
    @Valid
    private List<@NotNull @ValidLocationURI String> locations;

    @Column(name = "subject_public_key_info")
    @Getter
    @Setter
    @NotNull
    @ValidPublicKeyInfo
    private String subjectPublicKeyInfo;
}
