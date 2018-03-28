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
import net.ripe.rpki.commons.crypto.util.CertificateRepositoryObjectFactory;
import net.ripe.rpki.commons.validation.ValidationResult;
import net.ripe.rpki.validator3.util.Sha256;

import javax.persistence.Basic;
import javax.persistence.Entity;
import javax.persistence.OneToOne;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.Arrays;
import java.util.Optional;

import static net.ripe.rpki.commons.validation.ValidationString.VALIDATOR_RPKI_OBJECT_HASH_MATCHES;

@Entity
public class EncodedRpkiObject extends AbstractEntity {

    @OneToOne(optional = false)
    @NotNull
    private RpkiObject rpkiObject;

    @Basic
    @Getter
    @NotNull
    @NotEmpty
    @Size(max = RpkiObject.MAX_SIZE)
    private byte[] encoded;

    protected EncodedRpkiObject() {
    }

    public EncodedRpkiObject(byte[] encoded) {
        this.encoded = encoded;
    }

    public EncodedRpkiObject(RpkiObject rpkiObject, byte[] encoded) {
        this.rpkiObject = rpkiObject;
        this.encoded = encoded;
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
}
