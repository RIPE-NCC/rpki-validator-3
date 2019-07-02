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

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import net.ripe.rpki.commons.validation.ValidationStatus;
import net.ripe.rpki.validator3.storage.Binary;
import net.ripe.rpki.validator3.storage.data.Base;
import org.springframework.context.MessageSource;
import org.springframework.context.MessageSourceResolvable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@EqualsAndHashCode(callSuper = true)
@ToString
@Binary
public class ValidationCheck extends Base<ValidationCheck> implements MessageSourceResolvable {

    public enum Status {
        WARNING, ERROR
    }

    @Getter
    private String location;

    private String status;

    @Getter
    private String key;

    @Getter
    private List<String> parameters = new ArrayList<>();

    protected ValidationCheck() {
    }

    public ValidationCheck(String location, net.ripe.rpki.commons.validation.ValidationCheck check) {
        this.location = location;
        this.status = mapStatus(check.getStatus()).name();
        this.key = check.getKey();
        this.parameters = Arrays.asList(check.getParams());
    }

    public ValidationCheck(String location, Status status, String key, String... parameters) {
        this.location = location;
        this.status = status.name();
        this.key = key;
        this.parameters = Arrays.asList(parameters);
    }

    public Status getStatus() {
        return Status.valueOf(status);
    }

    @Override
    public String[] getCodes() {
        return new String[] { key + "." + status.toLowerCase() };
    }

    @Override
    public Object[] getArguments() {
        return parameters.toArray(new String[0]);
    }

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
}
