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
package net.ripe.rpki.validator3.storage.data;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import net.ripe.rpki.validator3.api.util.InstantWithoutNanos;
import net.ripe.rpki.validator3.domain.constraints.ValidLocationURI;
import net.ripe.rpki.validator3.storage.Binary;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.math.BigInteger;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

@EqualsAndHashCode(callSuper = true)
@Data
@Binary
@ToString
public class RpkiRepository extends Base<RpkiRepository> {

    public static final String TYPE = "rpki-repository";

    public enum Type {
        RRDP, RSYNC, RSYNC_PREFETCH
    }

    public enum Status {
        PENDING, FAILED, DOWNLOADED
    }

    @NotNull
    private String type;

    @NotNull
    private String status;

    private InstantWithoutNanos lastDownloadedAt;

    @NotEmpty
    private Set<Ref<TrustAnchor>> trustAnchors = new HashSet<>();

    @ValidLocationURI
    private String rsyncRepositoryUri;

    @ValidLocationURI
    private String rrdpNotifyUri;

    private String rrdpSessionId;

    private BigInteger rrdpSerial;

    private Ref<RpkiRepository> parentRepository;

    public RpkiRepository() {
    }

    public RpkiRepository(Ref<TrustAnchor> trustAnchor, @NotNull @ValidLocationURI String location, Type type) {
        addTrustAnchor(trustAnchor);
        this.status = Status.PENDING.name();
        switch (type) {
            case RRDP:
                this.type = Type.RRDP.name();
                this.rrdpNotifyUri = location;
                break;
            case RSYNC:
            case RSYNC_PREFETCH:
                this.type = type.name();
                this.rsyncRepositoryUri = location;
                break;
        }
    }

    public @ValidLocationURI @NotNull String getLocationUri() {
        return Optional.ofNullable(rrdpNotifyUri).orElse(rsyncRepositoryUri);
    }

    public void addTrustAnchor(Ref<TrustAnchor> trustAnchor) {
        this.trustAnchors.add(trustAnchor);
    }

    public void removeTrustAnchor(Ref<TrustAnchor> trustAnchor) {
        this.trustAnchors.remove(trustAnchor);
    }

    public boolean isPending() {
        return Status.valueOf(status) == Status.PENDING;
    }

    public boolean isFailed() {
        return Status.valueOf(status) == Status.FAILED;
    }

    public boolean isDownloaded() {
        return Status.valueOf(status) == Status.DOWNLOADED;
    }

    public void setFailed() {
        setFailed(InstantWithoutNanos.now());
    }

    public void setFailed(InstantWithoutNanos lastDownloadedAt) {
        this.status = Status.FAILED.name();
        this.lastDownloadedAt = lastDownloadedAt;
    }


    public void setDownloaded() {
        setDownloaded(InstantWithoutNanos.now());
    }

    public void setDownloaded(InstantWithoutNanos lastDownloadedAt) {
        this.status = Status.DOWNLOADED.name();
        this.lastDownloadedAt = lastDownloadedAt;
    }

    public Type getType() {
        return Type.valueOf(type);
    }

    public Status getStatus() {
        return Status.valueOf(status);
    }

    public void setType(Type type) {
        this.type = type.name();
    }
}
