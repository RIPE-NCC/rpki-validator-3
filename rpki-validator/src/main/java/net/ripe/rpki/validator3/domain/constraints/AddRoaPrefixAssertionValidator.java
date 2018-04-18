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
package net.ripe.rpki.validator3.domain.constraints;

import net.ripe.ipresource.IpRange;
import net.ripe.rpki.validator3.api.roaprefixassertions.AddRoaPrefixAssertion;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

import static net.ripe.ipresource.IpResourceType.IPv4;
import static net.ripe.ipresource.IpResourceType.IPv6;

public class AddRoaPrefixAssertionValidator implements ConstraintValidator<ValidAddRoaPrefixAssertion, AddRoaPrefixAssertion> {

    private final int MAX_LENGTH_IPV4 = 32;
    private final int MAX_LENGTH_IPV6 = 128;

    @Override
    public boolean isValid(AddRoaPrefixAssertion value, ConstraintValidatorContext context) {
        if (value.getAsn() == null && value.getPrefix() == null) {
            return false;
        }
        if (value.getPrefix() != null && value.getMaximumLength() != null) {
            final IpRange ipRange;
            try {
                ipRange = IpRange.parse(value.getPrefix());
            } catch (Exception e) {
                return false;
            }
            if (IPv4.equals(ipRange.getType())) {
                if (value.getMaximumLength() > MAX_LENGTH_IPV4) {
                    setReturnMessage(context, "MAX_LENGTH_LONGER_32");
                    return false;
                }
            }
            if (IPv6.equals(ipRange.getType())) {
                if (value.getMaximumLength() > MAX_LENGTH_IPV6) {
                    setReturnMessage(context, "MAX_LENGTH_LONGER_128");
                    return false;
                }
            }
            if (value.getMaximumLength() < ipRange.getPrefixLength()) {
                setReturnMessage(context, "MAX_LENGTH_LONGER_PREFIX_LENGTH");
                return false;
            }
         }
        return true;
    }

    private void setReturnMessage(ConstraintValidatorContext context, String message) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(message).addConstraintViolation();
    }
}
