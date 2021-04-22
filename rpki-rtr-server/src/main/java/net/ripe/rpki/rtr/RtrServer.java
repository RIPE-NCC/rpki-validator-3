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

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.handler.traffic.ChannelTrafficShapingHandler;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.rtr.adapter.netty.PduCodec;
import net.ripe.rpki.rtr.domain.RtrCache;
import net.ripe.rpki.rtr.domain.RtrClients;
import net.ripe.rpki.rtr.domain.SerialNumber;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Provider;
import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;


@Slf4j
@Service
public class RtrServer {

    public static final String DEFAULT_RTR_HOST = "localhost";
    public static final int DEFAULT_RTR_PORT = 9178;

    private String address;
    private int port;

    private final RtrCache rtrCache;
    private final RtrClients clients;
    private final Provider<RtrClientHandler> rtrClientHandlerProvider;

    @Autowired
    public RtrServer(
            @Value("${rtr.server.address}") String address,
            @Value("${rtr.server.port}") int port,
            RtrCache rtrCache,
            RtrClients clients,
            Provider<RtrClientHandler> rtrClientHandlerProvider) {
        setAddress(address);
        setPort(port);
        this.rtrCache = rtrCache;
        this.clients = clients;
        this.rtrClientHandlerProvider = rtrClientHandlerProvider;
    }

    private void setAddress(String address) {
        this.address = !StringUtils.isEmpty(address) ? address : DEFAULT_RTR_HOST;
    }

    private void setPort(int port) {
        this.port = port == -1 ? DEFAULT_RTR_PORT : port;
    }

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;

    @PostConstruct
    public void run() {
        new Thread(() -> {
            try {
                runNetty();
            } catch (InterruptedException e) {
                log.error("Error running Netty server");
            }
        }).start();
    }

    @PreDestroy
    public void stop() {
        shutdownWorkers();
    }

    public void expireOldDeltas() {
        SerialNumber lowestSerialNumber = clients.getLowestSerialNumber().orElse(rtrCache.getSerialNumber());
        Set<SerialNumber> forgottenDeltas = rtrCache.forgetDeltasBefore(lowestSerialNumber);
        if (!forgottenDeltas.isEmpty()) {
            log.info("removed deltas for serial numbers {}", forgottenDeltas.stream().map(sn -> String.valueOf(sn.getValue())).sorted().collect(Collectors.joining(", ")));
        }
    }

    public void disconnectInactiveClients() {
        int disconnected = clients.disconnectInactive(Instant.now());
        if (disconnected > 0) {
            log.info("disconnected {} inactive clients", disconnected);
        }
    }

    private void runNetty() throws InterruptedException {
        bossGroup = new NioEventLoopGroup();
        workerGroup = new NioEventLoopGroup();
        try {
            final ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    public void initChannel(SocketChannel ch) {
                        RtrClientHandler rtrClientHandler = rtrClientHandlerProvider.get();
                        ChannelTrafficShapingHandler traffic = new ChannelTrafficShapingHandler(0) {
                            @Override
                            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                                rtrClientHandler.exceptionCaught(ctx, cause);
                            }
                        };
                        rtrClientHandler.setTrafficShapingHandler(traffic);
                        ch.pipeline().addLast(traffic, new PduCodec(), new ChunkedWriteHandler(), rtrClientHandler);
                    }
                })
                .option(ChannelOption.SO_BACKLOG, 128)
                .childOption(ChannelOption.SO_KEEPALIVE, true);

            log.info("Running RTR at port {}", port);

            final ChannelFuture f = b.bind(address, port).sync();
            f.channel().closeFuture().sync();
        } finally {
            shutdownWorkers();
        }
    }

    private void shutdownWorkers() {
        clients.clear();
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
    }


}
