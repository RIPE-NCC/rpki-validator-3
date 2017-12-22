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

import com.google.common.base.Objects;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import net.ripe.rpki.validator3.domain.constraints.ValidLocationURI;

import javax.persistence.Basic;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.ManyToMany;
import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.math.BigInteger;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Entity
@ToString
public class RpkiRepository extends AbstractEntity {

    public static final String TYPE = "rpki-repository";

    public enum Type {
        RRDP, RSYNC, RSYNC_PREFETCH
    }

    public enum Status {
        PENDING, FAILED, DOWNLOADED
    }

    @Basic(optional = false)
    @Enumerated(EnumType.STRING)
    @NotNull
    @Getter
    @Setter
    private Type type;

    @Basic(optional = false)
    @Enumerated(EnumType.STRING)
    @NotNull
    @Getter
    private Status status;

    @Basic
    @Getter
    private Instant lastDownloadedAt;

    @ManyToMany
    @JoinTable(joinColumns = @JoinColumn(name = "rpki_repository_id"), inverseJoinColumns = @JoinColumn(name = "trust_anchor_id"))
    @NotNull
    @NotEmpty
    @Getter
    private Set<TrustAnchor> trustAnchors = new HashSet<>();

    @Basic
    @ValidLocationURI
    @Getter
    private String rsyncRepositoryUri;

    @Basic
    @ValidLocationURI
    @Getter
    private String rrdpNotifyUri;

    @Basic
    @Getter
    @Setter
    private String rrdpSessionId;

    @Basic
    @Getter
    @Setter
    private BigInteger rrdpSerial;

    protected RpkiRepository() {
    }

    public RpkiRepository(@NotNull @Valid TrustAnchor trustAnchor, @NotNull @ValidLocationURI String location, Type type) {
        addTrustAnchor(trustAnchor);
        this.status = Status.PENDING;
        switch (type) {
            case RRDP:
                this.type = Type.RRDP;
                this.rrdpNotifyUri = location;
                break;
            case RSYNC: case RSYNC_PREFETCH:
                this.type = type;
                this.rsyncRepositoryUri = location;
                break;
        }
    }

    public @ValidLocationURI @NotNull String getLocationUri() {
        return Objects.firstNonNull(rrdpNotifyUri, rsyncRepositoryUri);
    }

    public void addTrustAnchor(@NotNull @Valid TrustAnchor trustAnchor) {
        this.trustAnchors.add(trustAnchor);
    }

    public void removeTrustAnchor(@NotNull @Valid TrustAnchor trustAnchor) {
        this.trustAnchors.remove(trustAnchor);
    }

    public boolean isPending() {
        return status == Status.PENDING;
    }

    public boolean isFailed() {
        return status == Status.FAILED;
    }

    public boolean isDownloaded() {
        return status == Status.DOWNLOADED;
    }

    public void setFailed() {
        setFailed(Instant.now());
    }

    public void setFailed(Instant lastDownloadedAt) {
        this.status = Status.FAILED;
        this.lastDownloadedAt = lastDownloadedAt;
    }


    public void setDownloaded() {
        setDownloaded(Instant.now());
    }

    public void setDownloaded(Instant lastDownloadedAt) {
        this.status = Status.DOWNLOADED;
        this.lastDownloadedAt = lastDownloadedAt;
    }
}
