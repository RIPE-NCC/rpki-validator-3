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
package net.ripe.rpki.rtr.api;

import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.rtr.domain.RtrCache;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.stream.Stream;

@io.swagger.annotations.Api(tags = "Cache")
@RestController
@RequestMapping(path = "/cache", produces = Api.API_MIME_TYPE)
@Slf4j
public class CacheController {

    @Autowired
    private RtrCache cache;

    @GetMapping
    public ApiResponse<Cache> list() {
        RtrCache.State state = cache.getState();
        return ApiResponse.data(new Cache(
            state.getContent().isReady(),
            state.getContent().getSessionId(),
            state.getContent().getSerialNumber().getValue(),
            state.getContent().getAnnouncements().size(),
            state.getDeltas().entrySet().stream().map(entry -> new Delta(
                entry.getKey().getValue(),
                entry.getValue().getAnnouncements().size(),
                entry.getValue().getWithdrawals().size()
            ))
        ));
    }

    @Value
    public static class Cache {
        boolean ready;
        short sessionId;
        int serialNumber;
        int announcementsCount;

        Stream<Delta> deltas;
    }

    @Value
    public static class Delta {
        int serialNumber;
        int announcementsCount;
        int withdrawalsCount;
    }
}
