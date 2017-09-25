package net.ripe.rpki.validator3.domain;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

@Entity
public class TrustAnchor {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    @Getter
    private long id = -1;

    @Basic
    @Getter
    @Setter
    @NotNull
    @Size(min = 1, max = 1000)
    private String name;

    @Column(name = "rsync_uri")
    @Getter
    @Setter
    @NotNull
    @Size(min = 1, max = 16000)
    private String rsyncURI;

    @Column(name = "subject_public_key_info")
    @Getter
    @Setter
    @NotNull
    @Size(min = 100, max = 2000)
    private String subjectPublicKeyInfo;
}
