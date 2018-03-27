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
import net.ripe.ipresource.IpRange;
import net.ripe.rpki.validator3.domain.BgpSecAssertion;
import net.ripe.rpki.validator3.domain.BgpSecAssertions;
import net.ripe.rpki.validator3.domain.RoaPrefixAssertion;
import net.ripe.rpki.validator3.util.Transactions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import javax.transaction.Transactional;
import javax.validation.Valid;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
@Transactional
@Validated
@Slf4j
public class BgpSecAssertionsService {

    @Autowired
    private BgpSecAssertions bgpSecAssertions;

    public long execute(@Valid AddBgpSecAssertion command) {
        BgpSecAssertion entity = new BgpSecAssertion(
            command.getAsn() == null ? null : Asn.parse(command.getAsn()).longValue(),
            command.getSki(),
            command.getPublicKey(),
            command.getComment()
        );

        return add(entity);
    }

    long add(BgpSecAssertion entity) {
        bgpSecAssertions.add(entity);

        log.info("added ROA prefix assertion '{}'", entity);
        return entity.getId();
    }

    public void remove(long roaPrefixAssertionId) {
        BgpSecAssertion entity = bgpSecAssertions.get(roaPrefixAssertionId);
        if (entity != null) {
            bgpSecAssertions.remove(entity);
        }
    }

    public Stream<BgpSecAssertion> all() {
        return bgpSecAssertions.all();
    }

    public void clear() {
        bgpSecAssertions.clear();
    }

}
