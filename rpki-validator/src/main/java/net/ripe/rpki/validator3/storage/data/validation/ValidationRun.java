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
package net.ripe.rpki.validator3.storage.data.validation;

import lombok.Getter;
import lombok.Setter;
import net.ripe.rpki.commons.validation.ValidationResult;
import net.ripe.rpki.commons.validation.ValidationStatus;
import net.ripe.rpki.validator3.api.util.InstantWithoutNanos;
import net.ripe.rpki.validator3.storage.data.Base;

import java.util.ArrayList;
import java.util.List;


/**
 * Represents the a single run of validating a single trust anchor and all it's child CAs and related RPKI objects.
 */
public abstract class ValidationRun extends Base<ValidationRun> {

    public enum Status {
        RUNNING,
        SUCCEEDED,
        FAILED
    }

    @Getter
    @Setter
    private InstantWithoutNanos completedAt;

    @Setter
    private String status = Status.RUNNING.name();

    @Getter
    @Setter
    private List<ValidationCheck> validationChecks = new ArrayList<>();

    public abstract String getType();

    public boolean isSucceeded() {
        return Status.SUCCEEDED.name().equals(this.status);
    }
    public boolean isFailed() {
        return Status.FAILED.name().equals(this.status);
    }

    public void setSucceeded() {
        this.completedAt = InstantWithoutNanos.now();
        this.status = Status.SUCCEEDED.name();
    }

    public void setFailed() {
        this.completedAt = InstantWithoutNanos.now();
        this.status = Status.FAILED.name();
    }

    public Status getStatus() {
        return Status.valueOf(status);
    }

    public void completeWith(ValidationResult validationResult) {
        validationResult.getValidatedLocations().forEach(location ->
                validationResult.getAllValidationChecksForLocation(location).stream()
                        .filter(check -> check.getStatus() != ValidationStatus.PASSED)
                        .map(check -> new ValidationCheck(location.getName(), check))
                        .forEachOrdered(this::addCheck));

        if (!isFailed()) {
            setSucceeded();
        }
    }

    public void addCheck(ValidationCheck validationCheck) {
        this.validationChecks.add(validationCheck);
    }

    public void addChecks(ValidationResult validationResult) {
        validationResult.getAllValidationChecksForCurrentLocation().forEach(c -> {
            if (c.getStatus() != ValidationStatus.PASSED) {
                final ValidationCheck.Status status = ValidationCheck.mapStatus(c.getStatus());
                addCheck(new ValidationCheck(validationResult.getCurrentLocation().getName(), status, c.getKey(), c.getParams()));
            }
        });
    }

    public int countChecks(ValidationCheck.Status status) {
        return Math.toIntExact(validationChecks.stream().filter(vc -> vc.getStatus() == status).count());
    }

    public abstract void visit(Visitor visitor);

    public interface Visitor<T> {
        default void accept(CertificateTreeValidationRun validationRun) {
        }
        default void accept(RrdpRepositoryValidationRun validationRun) {
        }
        default void accept(RsyncRepositoryValidationRun validationRun) {
        }
        default void accept(TrustAnchorValidationRun validationRun) {
        }
    }
}
