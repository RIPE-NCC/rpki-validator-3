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
package net.ripe.rpki.validator3.api.validationruns;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import net.ripe.rpki.validator3.storage.data.validation.ValidationCheck;

import java.util.List;

@Data(staticConstructor = "of")
@ApiModel(value = "ValidationCheck")
public class ValidationCheckResource {
    @ApiModelProperty(required = true, position = 1, example = "rsync://rpki.cnnic.cn/rpki/A9162E3D0000/BBYptqnqt8sTJOo5ePA3lviJtUA.crl")
    final String location;
    @ApiModelProperty(required = true, position = 2, example = "WARNING")
    final ValidationCheck.Status status;
    @ApiModelProperty(required = true, position = 4, example = "rl.next.update.before.now")
    final String key;
    @ApiModelProperty(required = true, position = 3, example = "[\"2020-01-09T23:15:45.000Z\"]")
    final List<String> parameters;
    @ApiModelProperty(required = false, position = 5, example = "CRL next update was expected on or before 2020-01-09T23:15:45.000Z")
    final String formattedMessage;

    public static ValidationCheckResource of(ValidationCheck check, String formattedMessage) {
        return of(check.getLocation(), check.getStatus(), check.getKey(), check.getParameters(), formattedMessage);
    }
}
