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
import net.ripe.rpki.commons.validation.ValidationLocation;
import net.ripe.rpki.commons.validation.ValidationResult;
import net.ripe.rpki.commons.validation.ValidationStatus;

import javax.persistence.*;
import javax.validation.constraints.NotNull;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static net.ripe.rpki.validator3.domain.ValidationCheck.mapStatus;


/**
 * Represents the a single run of validating a single trust anchor and all it's child CAs and related RPKI objects.
 */
@Entity
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "TYPE", columnDefinition = "CHAR(2)")
public abstract class ValidationRun extends AbstractEntity {

    public enum Status {
        RUNNING,
        SUCCEEDED,
        FAILED
    }

    @Basic
    @Getter
    private Instant completedAt;

    @Enumerated(value = EnumType.STRING)
    @NotNull
    @Getter
    private Status status = Status.RUNNING;

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "validationRun")
    @Getter
    private List<ValidationCheck> validationChecks = new ArrayList<>();

    @SuppressWarnings("unused")
    protected ValidationRun() {
        super();
    }

    public abstract String getType();

    public boolean isSucceeded() {
        return this.status == Status.SUCCEEDED;
    }
    public boolean isFailed() {
        return this.status == Status.FAILED;
    }

    public void setSucceeded() {
        this.completedAt = Instant.now();
        this.status = Status.SUCCEEDED;
    }

    public void setFailed() {
        this.completedAt = Instant.now();
        this.status = Status.FAILED;
    }

    public void completeWith(ValidationResult validationResult) {
        for (ValidationLocation location : validationResult.getValidatedLocations()) {
            for (net.ripe.rpki.commons.validation.ValidationCheck check : validationResult.getAllValidationChecksForLocation(location)) {
                if (check.getStatus() != ValidationStatus.PASSED) {
                    ValidationCheck validationCheck = new ValidationCheck(this, location.getName(), check);
                    addCheck(validationCheck);
                }
            }
        }

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
                final ValidationCheck.Status status = mapStatus(c.getStatus());
                addCheck(new ValidationCheck(this, validationResult.getCurrentLocation().getName(), status, c.getKey(), c.getParams()));
            }
        });
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
