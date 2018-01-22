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
package net.ripe.rpki.rtr;

import io.netty.channel.embedded.EmbeddedChannel;
import net.ripe.rpki.rtr.domain.RtrCache;
import net.ripe.rpki.rtr.domain.RtrClients;
import net.ripe.rpki.rtr.domain.SerialNumber;
import net.ripe.rpki.rtr.domain.pdus.CacheResetPdu;
import net.ripe.rpki.rtr.domain.pdus.CacheResponsePdu;
import net.ripe.rpki.rtr.domain.pdus.EndOfDataPdu;
import net.ripe.rpki.rtr.domain.pdus.ResetQueryPdu;
import net.ripe.rpki.rtr.domain.pdus.SerialQueryPdu;
import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

@Ignore
public class RtrServerTest {

    @Test
    public void should_reply_with_cache_reset_is_session_id_is_different() {
        final RtrCache rtrCache = new RtrCache();
        final RtrClients clients = new RtrClients();
        final short sessionId = (short) (rtrCache.getSessionId() + 10);
        final SerialNumber serial = SerialNumber.of(20);
        EmbeddedChannel channel = new EmbeddedChannel(new RtrServer.RtrClientHandler(rtrCache, clients));
        channel.writeInbound(SerialQueryPdu.of(sessionId, serial));
        CacheResetPdu resetPdu = channel.readOutbound();
        assertNotNull(resetPdu);
        assertNull(channel.readOutbound());
    }

    @Test
    public void should_reply_with_cache_reset_if_session_id_the_same() {
        final RtrCache rtrCache = new RtrCache();
        final RtrClients clients = new RtrClients();
        EmbeddedChannel channel = new EmbeddedChannel(new RtrServer.RtrClientHandler(rtrCache, clients));
        channel.writeInbound(SerialQueryPdu.of(rtrCache.getSessionId(), rtrCache.getSerialNumber()));
        final CacheResponsePdu responsePdu = channel.readOutbound();
        assertEquals(rtrCache.getSessionId(), responsePdu.getSessionId());
        final EndOfDataPdu endOfDataPdu = channel.readOutbound();
        assertEquals(rtrCache.getSessionId(), endOfDataPdu.getSessionId());
        assertEquals(rtrCache.getSerialNumber(), endOfDataPdu.getSerialNumber());
        assertEquals(7200, endOfDataPdu.getExpireInterval());
        assertEquals(3600, endOfDataPdu.getRefreshInterval());
        assertEquals(600, endOfDataPdu.getRetryInterval());
    }

    @Test
    public void should_reply_with_cache_reset_reset_query_pdu() {
        final RtrCache rtrCache = new RtrCache();
        final RtrClients clients = new RtrClients();
        EmbeddedChannel channel = new EmbeddedChannel(new RtrServer.RtrClientHandler(rtrCache, clients));
        channel.writeInbound(ResetQueryPdu.of());
        final CacheResponsePdu responsePdu = channel.readOutbound();
        assertEquals(rtrCache.getSessionId(), responsePdu.getSessionId());
        final EndOfDataPdu endOfDataPdu = channel.readOutbound();
        assertEquals(rtrCache.getSessionId(), endOfDataPdu.getSessionId());
        assertEquals(rtrCache.getSerialNumber(), endOfDataPdu.getSerialNumber());
        assertEquals(7200, endOfDataPdu.getExpireInterval());
        assertEquals(3600, endOfDataPdu.getRefreshInterval());
        assertEquals(600, endOfDataPdu.getRetryInterval());
    }


}