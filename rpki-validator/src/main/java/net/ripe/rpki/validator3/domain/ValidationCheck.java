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
import net.ripe.rpki.commons.validation.ValidationStatus;
import org.springframework.context.MessageSource;
import org.springframework.context.MessageSourceResolvable;

import javax.annotation.Nullable;
import javax.persistence.Basic;
import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.FetchType;
import javax.persistence.ManyToOne;
import javax.persistence.OrderColumn;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@Entity
public class ValidationCheck extends AbstractEntity implements MessageSourceResolvable {

    public enum Status {
        WARNING, ERROR
    }

    @ManyToOne(optional = false, cascade = CascadeType.PERSIST)
    @NotNull
    @Getter
    private ValidationRun validationRun;

    @Basic(optional = false)
    @NotEmpty
    @NotNull
    @Getter
    private String location;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    @NotNull
    @Getter
    private Status status;

    @Basic(optional = false)
    @NotEmpty
    @NotNull
    @Getter
    private String key;

    @ElementCollection(fetch = FetchType.EAGER)
    @OrderColumn
    @Getter
    @NotNull
    private List<String> parameters = new ArrayList<>();

    protected ValidationCheck() {
    }

    public ValidationCheck(ValidationRun validationRun, String location, net.ripe.rpki.commons.validation.ValidationCheck check) {
        this.validationRun = validationRun;
        this.location = location;
        this.status = mapStatus(check.getStatus());
        this.key = check.getKey();
        this.parameters = Arrays.asList(check.getParams());
    }

    public ValidationCheck(ValidationRun validationRun, String location, Status status, String key, String... parameters) {
        this.validationRun = validationRun;
        this.location = location;
        this.status = status;
        this.key = key;
        this.parameters.addAll(Arrays.asList(parameters));
    }

    @Override
    public String[] getCodes() {
        return new String[] { key + "." + status.name().toLowerCase() };
    }

    @Override
    public Object[] getArguments() {
        return parameters.toArray(new String[parameters.size()]);
    }

    @Nullable
    public String formattedMessage(MessageSource messageSource, Locale locale) {
        return messageSource.getMessage(this, locale);
    }

    static Status mapStatus(ValidationStatus status) {
        switch (status) {
            case PASSED:
                throw new IllegalArgumentException("PASSED checks should not be stored: " + status);
            case WARNING:
                return Status.WARNING;
            case ERROR: case FETCH_ERROR:
                return Status.ERROR;
        }
        throw new IllegalArgumentException("Unknown status: " + status);
    }

    @Override
    public String toString() {
        return toStringBuilder()
            .append("location", location)
            .append("status", status)
            .append("key", key)
            .append("parameters", parameters)
            .build();
    }
}
