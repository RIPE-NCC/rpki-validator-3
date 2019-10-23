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
package net.ripe.rpki.validator3.api.bgpsec;

import lombok.extern.slf4j.Slf4j;
import net.ripe.ipresource.Asn;
import net.ripe.rpki.validator3.api.slurm.SlurmStore;
import net.ripe.rpki.validator3.api.slurm.dtos.Slurm;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import javax.validation.Valid;
import java.util.stream.Stream;

@Component
@Validated
@Slf4j
public class BgpSecAssertionsService {

    private final SlurmStore slurmStore;

    @Autowired
    public BgpSecAssertionsService(SlurmStore slurmStore) {
        this.slurmStore = slurmStore;
    }

    public long execute(@Valid AddBgpSecAssertion command) {
        final long id = slurmStore.nextId();
        return slurmStore.updateWith(slurmExt -> {
            final Slurm.SlurmBgpSecAssertion slurmBgpSecAssertion = new Slurm.SlurmBgpSecAssertion();
            slurmBgpSecAssertion.setAsn(Asn.parse(command.getAsn()).longValue());
            slurmBgpSecAssertion.setPublicKey(command.getPublicKey());
            slurmBgpSecAssertion.setSki(command.getSki());
            slurmBgpSecAssertion.setComment(command.getComment());
            slurmExt.getBgpsecAssertions().put(id, slurmBgpSecAssertion);
            return id;
        });
    }

    public void remove(long id) {
        slurmStore.updateWith(slurmExt -> {
            slurmExt.getPrefixAssertions().remove(id);
        });
    }

    public Stream<BgpSecAssertion> all() {
        return slurmStore.read().getBgpsecAssertions()
                .entrySet().stream()
                .map(e -> {
                    Slurm.SlurmBgpSecAssertion v = e.getValue();
                    return new BgpSecAssertion(e.getKey(), v.getAsn(), v.getComment(), v.getSki(), v.getPublicKey());
                });
    }

    public void clear() {
        slurmStore.updateWith(slurmExt -> {
            slurmExt.getBgpsecAssertions().clear();
        });
    }

}
