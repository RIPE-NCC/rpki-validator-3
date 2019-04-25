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
package net.ripe.rpki.validator3.storage.encoding.custom.validation;

import net.ripe.rpki.validator3.storage.encoding.custom.BaseCoder;
import net.ripe.rpki.validator3.storage.encoding.custom.Coders;
import net.ripe.rpki.validator3.storage.encoding.custom.Encoded;
import net.ripe.rpki.validator3.storage.encoding.custom.Tags;
import net.ripe.rpki.validator3.storage.data.validation.ValidationRun;

import java.util.Map;

public class ValidationRunCoder {

    private final static short COMPLETED_AT_TAG = Tags.unique(121);
    private final static short VALIDATION_CHECKS_TAG = Tags.unique(122);
    private final static short STATUS_TAG = Tags.unique(123);

    private static final ValidationCheckCoder vcCoder = new ValidationCheckCoder();

    public static void toBytes(ValidationRun validationRun, Encoded encoded) {
        BaseCoder.toBytes(validationRun, encoded);

        encoded.appendNotNull(STATUS_TAG, validationRun.getStatus(), s -> Coders.toBytes(s.name()));
        encoded.appendNotNull(COMPLETED_AT_TAG, validationRun.getCompletedAt(), Coders::toBytes);
        if (validationRun.getValidationChecks() != null && !validationRun.getValidationChecks().isEmpty()) {
            encoded.append(VALIDATION_CHECKS_TAG, Coders.toBytes(validationRun.getValidationChecks(), vcCoder::toBytes));
        }
    }

    public static void fromBytes(Map<Short, byte[]> content, ValidationRun validationRun) {
        BaseCoder.fromBytes(content, validationRun);

        Encoded.field(content, STATUS_TAG).ifPresent(b -> validationRun.setStatus(Coders.toString(b)));
        Encoded.field(content, COMPLETED_AT_TAG).ifPresent(b -> validationRun.setCompletedAt(Coders.toInstant(b)));
        Encoded.field(content, VALIDATION_CHECKS_TAG).ifPresent(b ->
                validationRun.setValidationChecks(Coders.fromBytes(b, vcCoder::fromBytes)));
    }

}
