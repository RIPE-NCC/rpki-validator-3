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
package net.ripe.rpki.validator3.rrdp;

import net.ripe.rpki.validator3.util.Hex;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class RrdpParserTest {

    @Test
    public void should_parse_snapshot() throws Exception {
        final Snapshot snapshot = new RrdpParser().snapshot(fileIS("rrdp/snapshot1.xml"));
        assertNotNull(snapshot.asMap().get("rsync://bandito.ripe.net/repo/671570f06499fbd2d6ab76c4f22566fe49d5de60.cer"));
    }

    @Test
    public void should_parse_delta() throws Exception {
        final Delta delta = new RrdpParser().delta(fileIS("rrdp/delta1.xml"));
        final String uri1 = "rsync://bandito.ripe.net/repo/3a87a4b1-6e22-4a63-ad0f-06f83ad3ca16/default/671570f06499fbd2d6ab76c4f22566fe49d5de60.mft";
        DeltaElement e1 = delta.asMap().get(uri1);
        assertEquals(uri1, ((DeltaPublish)e1).uri);
        assertEquals("226AB8CD3C887A6EBDDDF317F2FAFC9CF3EFC5D43A86347AC0FEFFE4DC0F607E", Hex.format(((DeltaPublish)e1).getHash().get()));

        final String uri2 = "rsync://bandito.ripe.net/repo/3a87a4b1-6e22-4a63-ad0f-06f83ad3ca16/default/671570f06499fbd2d6ab76c4f22566fe49d5de60.crl";
        DeltaElement e2 = delta.asMap().get(uri2);
        assertEquals(uri2, ((DeltaPublish)e2).uri);
        assertEquals("2B551A6C10CCA04C174B0CEB3B64652A5534D1385BEAA40A55A68CB06055E6BB", Hex.format(((DeltaPublish)e2).getHash().get()));

        final String uri3 = "rsync://bandito.ripe.net/repo/3a87a4b1-6e22-4a63-ad0f-06f83ad3ca16/default/example.roa";
        DeltaElement e3 = delta.asMap().get(uri3);
        assertEquals(uri3, ((DeltaWithdraw)e3).uri);
        assertEquals("2B551A6C10CCA04C174B0CEB3B64652A5534D1385BEAA40A55A68CB06055E6BB", Hex.format(((DeltaWithdraw) e3).getHash()));
    }

    @Test
    public void should_parse_notification() throws Exception {
        final Notification notification = new RrdpParser().notification(fileIS("rrdp/notification1.xml"));
        assertEquals("9df4b597-af9e-4dca-bdda-719cce2c4e28", notification.sessionId);
        assertEquals("http://repo.net/repo/snapshot.xml", notification.snapshotUri);
        assertEquals("EEEA7F7AD96D85BBD1F7274FA7DA0025984A2AF3D5A0538F77BEC732ECB1B068", notification.snapshotHash);
        assertEquals(BigInteger.ONE, notification.serial);
        assertEquals(0, notification.deltas.size());
    }

    private static InputStream fileIS(String path) throws IOException {
        return Thread.currentThread().getContextClassLoader().getResourceAsStream(path);
    }

}