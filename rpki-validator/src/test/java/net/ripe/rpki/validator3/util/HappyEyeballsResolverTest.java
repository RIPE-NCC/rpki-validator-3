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

package net.ripe.rpki.validator3.util;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.util.Promise;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.junit.Ignore;
import org.junit.Test;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

public class HappyEyeballsResolverTest {

    private HappyEyeballsResolver subject = new HappyEyeballsResolver(new HttpClient());

    @Test
    public void should_recognise_ipv4_literal() {
        assertThat(subject.isLiteralIpAddress("10.0.0.10")).hasValue("10.0.0.10");
        assertThat(subject.isLiteralIpAddress("127.0.0.1")).hasValue("127.0.0.1");
        assertThat(subject.isLiteralIpAddress("567.0.0.1")).hasValue("567.0.0.1"); // true in this implementation
    }

    @Test
    public void should_not_recognise_ipv4_literal() {
        assertThat(subject.isLiteralIpAddress(".10.0.0.24")).isEmpty();
        assertThat(subject.isLiteralIpAddress("a10.0.0.24")).isEmpty();
        assertThat(subject.isLiteralIpAddress("10.0.0.24.b")).isEmpty();
        assertThat(subject.isLiteralIpAddress("10.0.0/24")).isEmpty();
        assertThat(subject.isLiteralIpAddress("127")).isEmpty();
        assertThat(subject.isLiteralIpAddress("")).isEmpty();
    }

    @Test
    public void should_recognise_ipv6_literal() {
        assertThat(subject.isLiteralIpAddress("db8::")).hasValue("db8::");
        assertThat(subject.isLiteralIpAddress("::1")).hasValue("::1");
        assertThat(subject.isLiteralIpAddress("0db8::1")).hasValue("0db8::1");
        assertThat(subject.isLiteralIpAddress("Db8:db9:1")).hasValue("Db8:db9:1"); // in this implementation
        assertThat(subject.isLiteralIpAddress("dB8:db9::1%eth0")).hasValue("dB8:db9::1%eth0");
        assertThat(subject.isLiteralIpAddress("fe80::b62e:99ff:fe46:41f2%en0")).hasValue("fe80::b62e:99ff:fe46:41f2%en0");
        assertThat(subject.isLiteralIpAddress("::FFFF:192.168.1.250")).hasValue("::FFFF:192.168.1.250");
    }

    @Test
    public void should_not_recognise_ipv6_literal() {
        assertThat(subject.isLiteralIpAddress("dbg::")).isEmpty();
        assertThat(subject.isLiteralIpAddress("db8:dead:beef::/26")).isEmpty();
        assertThat(subject.isLiteralIpAddress("127")).isEmpty();
        assertThat(subject.isLiteralIpAddress("")).isEmpty();
    }

    @Test
    @Ignore("intended for manual run only")
    public void connectToRealAddress() throws ExecutionException, InterruptedException {
        final SslContextFactory sslContextFactory = new SslContextFactory.Client(false);
        HttpClient httpClient = new HttpClient(sslContextFactory);
        httpClient.setExecutor(Executors.newCachedThreadPool());

        final CompletableFuture<List<InetSocketAddress>> future = new CompletableFuture<>();
        final Promise<List<InetSocketAddress>> promise = Promise.from(future);
        final HappyEyeballsResolver happyEyeballsResolver = new HappyEyeballsResolver(httpClient);
        final long startTime = System.nanoTime();
        happyEyeballsResolver.resolve("www.google.com", 80, promise);
        final List<InetSocketAddress> inetSocketAddresses = future.get();
        final long elapsed = System.nanoTime() - startTime;
        System.out.printf("Connected to: %s in %1.3fs %n",
                inetSocketAddresses.get(0).getAddress().getHostAddress(), elapsed/1e9);
        assertThat(inetSocketAddresses).isNotEmpty();
    }
}
