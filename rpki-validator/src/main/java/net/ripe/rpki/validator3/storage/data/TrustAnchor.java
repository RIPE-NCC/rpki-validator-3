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
import net.ripe.rpki.commons.crypto.util.CertificateRepositoryObjectFactory;
import net.ripe.rpki.commons.crypto.x509cert.X509ResourceCertificate;
import net.ripe.rpki.commons.validation.ValidationResult;
import net.ripe.rpki.validator3.domain.constraints.ValidLocationURI;
import net.ripe.rpki.validator3.domain.constraints.ValidPublicKeyInfo;
import net.ripe.rpki.validator3.storage.Binary;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@EqualsAndHashCode(callSuper = true)
@Binary
@ToString(exclude = {"encodedCertificate", "subjectPublicKeyInfo"})
@Data
public class TrustAnchor extends Base<TrustAnchor> {

    public static final String TYPE = "trust-anchor";

    private boolean preconfigured;

    private boolean initialCertificateTreeValidationRunCompleted;

    @NotNull
    @Size(min = 1, max = 1000)
    private String name;

    private List<@NotNull @ValidLocationURI String> locations = new ArrayList<>();

    @NotNull
    @ValidPublicKeyInfo
    private String subjectPublicKeyInfo;

    @ValidLocationURI
    private String rsyncPrefetchUri;

    @Size(max = RpkiObject.MAX_SIZE)
    private byte[] encodedCertificate;

    public TrustAnchor() {
    }

    public TrustAnchor(boolean preconfigured) {
        this.preconfigured = preconfigured;
        this.initialCertificateTreeValidationRunCompleted = false;
    }

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

    public void markInitialCertificateTreeValidationRunCompleted() {
        this.initialCertificateTreeValidationRunCompleted = true;
    }

    public List<URI> getLocationsByPreference() {
        return locations.stream()
            .map(URI::create)
            .sorted(Comparator.comparing(URI::getScheme))
            .collect(Collectors.toList());
    }
}
