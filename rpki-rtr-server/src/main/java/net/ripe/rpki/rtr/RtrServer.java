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

import fj.data.Either;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.rtr.adapter.netty.PduCodec;
import net.ripe.rpki.rtr.domain.RtrCache;
import net.ripe.rpki.rtr.domain.RtrClient;
import net.ripe.rpki.rtr.domain.RtrClients;
import net.ripe.rpki.rtr.domain.RtrDataUnit;
import net.ripe.rpki.rtr.domain.SerialNumber;
import net.ripe.rpki.rtr.domain.pdus.CacheResetPdu;
import net.ripe.rpki.rtr.domain.pdus.CacheResponsePdu;
import net.ripe.rpki.rtr.domain.pdus.EndOfDataPdu;
import net.ripe.rpki.rtr.domain.pdus.ErrorCode;
import net.ripe.rpki.rtr.domain.pdus.ErrorPdu;
import net.ripe.rpki.rtr.domain.pdus.Flags;
import net.ripe.rpki.rtr.domain.pdus.NotifyPdu;
import net.ripe.rpki.rtr.domain.pdus.Pdu;
import net.ripe.rpki.rtr.domain.pdus.PduParseException;
import net.ripe.rpki.rtr.domain.pdus.ResetQueryPdu;
import net.ripe.rpki.rtr.domain.pdus.SerialQueryPdu;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.ArrayDeque;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;


@Slf4j
@Service
public class RtrServer {

    public static final int DEFAULT_RTR_PORT = 9178;

    @Autowired
    private RtrCache rtrCache;

    private int port;

    @Autowired
    private RtrClients clients;

    public RtrServer(@Value("${rtr.server.port}") int port) {
        setPort(port);
    }

    private void setPort(int port) {
        if (port == -1) {
            port = DEFAULT_RTR_PORT;
        }
        this.port = port;
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

    @Scheduled(initialDelay = 10_000L, fixedDelay = 60_000L)
    public void expireOldDeltas() {
        SerialNumber lowestSerialNumber = clients.getLowestSerialNumber().orElse(rtrCache.getSerialNumber());
        Set<SerialNumber> forgottenDeltas = rtrCache.forgetDeltasBefore(lowestSerialNumber);
        if (!forgottenDeltas.isEmpty()) {
            log.info("removed deltas for serial numbers {}", forgottenDeltas.stream().map(sn -> String.valueOf(sn.getValue())).sorted().collect(Collectors.joining(", ")));
        }
    }

    private void runNetty() throws InterruptedException {
        bossGroup = new NioEventLoopGroup();
        workerGroup = new NioEventLoopGroup();
        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        public void initChannel(SocketChannel ch) {
                            ch.pipeline().addLast(new PduCodec(), new RtrClientHandler(rtrCache, clients));
                        }
                    })
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .childOption(ChannelOption.SO_KEEPALIVE, true);

            log.info("Running RTR at port {}", port);

            ChannelFuture f = b.bind(port).sync();
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


    @ToString(exclude = {"cache", "clients"})
    public static class RtrClientHandler extends SimpleChannelInboundHandler<Pdu> implements RtrClient {
        private static final int REFRESH_INTERVAL = 3600;
        private static final int RETRY_INTERVAL = 600;
        private static final int EXPIRE_INTERVAL = 7200;

        private ChannelHandlerContext ctx;
        private Pdu currentRequest = null;
        private Queue<Pdu> pending = new ArrayDeque<>();


        private SerialNumber clientSerialNumber = SerialNumber.zero();
        private SerialNumber latestNotifySerialNumber = SerialNumber.zero();

        private DateTime lastActive = DateTime.now();

        private final RtrCache cache;
        private final RtrClients clients;

        public RtrClientHandler(RtrCache cache, RtrClients clients) {
            this.cache = Objects.requireNonNull(cache, "cache");
            this.clients = clients;
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            super.channelActive(ctx);
            this.ctx = ctx;
            clients.register(this);
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            clients.unregister(this);
            super.channelInactive(ctx);
        }

        @Override
        public synchronized void channelRead0(ChannelHandlerContext ctx, Pdu pdu) {
            this.lastActive = DateTime.now();
            if (currentRequest != null) {
                pending.add(pdu);
                log.info("currently busy handling request, queuing {}", pdu);
                return;
            }

            currentRequest = pdu;

            ChannelFuture responseComplete = handleClientRequest(ctx, pdu);
            if (responseComplete != null) {
                responseComplete.addListener((f) -> requestHandlingCompleted());
            }
        }

        private synchronized void requestHandlingCompleted() {
            currentRequest = pending.poll();
            if (currentRequest == null) {
                log.info("finished processing all pending requests for {}", this);

                SerialNumber cacheSerialNumber = cache.getSerialNumber();
                if (cacheSerialNumber.isAfter(latestNotifySerialNumber)) {
                    log.info("Serial number updated since the last notification from {} to {}", latestNotifySerialNumber.getValue(), cacheSerialNumber.getValue());
                    latestNotifySerialNumber = cacheSerialNumber;
                    ctx.write(NotifyPdu.of(cache.getSessionId(), cacheSerialNumber));
                }
            } else {
                ChannelFuture channelFuture = handleClientRequest(ctx, currentRequest);
                if (channelFuture != null) {
                    channelFuture.addListener((f) -> requestHandlingCompleted());
                }
            }
        }

        private ChannelFuture handleClientRequest(ChannelHandlerContext ctx, Pdu pdu) {
            log.info("handling client request {}", pdu);
            ChannelFuture responseComplete = null;
            if (pdu instanceof SerialQueryPdu) {
                responseComplete = handleSerialQuery(ctx, (SerialQueryPdu) pdu);
            } else if (pdu instanceof ResetQueryPdu) {
                responseComplete = handleResetQuery(ctx, (ResetQueryPdu) pdu);
            } else if (pdu instanceof ErrorPdu) {
                log.error("error received from router {}, closing connection", pdu);
                ctx.close();
            } else {
                throw new IllegalStateException("unrecognized PDU " + pdu);
            }
            ctx.flush();
            return responseComplete;
        }

        private ChannelFuture handleSerialQuery(ChannelHandlerContext ctx, SerialQueryPdu serialQueryPdu) {
            if (!cache.isReady()) {
                return ctx.write(ErrorPdu.of(ErrorCode.NoDataAvailable, serialQueryPdu.toByteArray(), "no data available"));
            }

            Either<RtrCache.Delta, RtrCache.Content> deltaOrContent = cache.getDeltaOrContent(serialQueryPdu.getSerialNumber());

            if (deltaOrContent.isLeft()) {
                RtrCache.Delta delta = deltaOrContent.left().value();
                if (delta.getSessionId() != serialQueryPdu.getSessionId()) {
                    return ctx.write(CacheResetPdu.of());
                }

                clientSerialNumber = delta.getSerialNumber();

                ctx.write(CacheResponsePdu.of(delta.getSessionId()));
                for (RtrDataUnit dataUnit : delta.getAnnouncements()) {
                    ctx.write(dataUnit.toPdu(Flags.ANNOUNCEMENT));
                }
                for (RtrDataUnit dataUnit : delta.getWithdrawals()) {
                    ctx.write(dataUnit.toPdu(Flags.WITHDRAWAL));
                }
                return ctx.write(EndOfDataPdu.of(delta.getSessionId(), delta.getSerialNumber(), REFRESH_INTERVAL, RETRY_INTERVAL, EXPIRE_INTERVAL));
            } else {
                RtrCache.Content content = deltaOrContent.right().value();
                latestNotifySerialNumber = content.getSerialNumber();
                return ctx.write(CacheResetPdu.of());
            }
        }

        private ChannelFuture handleResetQuery(ChannelHandlerContext ctx, ResetQueryPdu resetQueryPdu) {
            RtrCache.Content content = cache.getCurrentContent();
            if (!content.isReady()) {
                return ctx.write(ErrorPdu.of(ErrorCode.NoDataAvailable, resetQueryPdu.toByteArray(), "no data available"));
            }

            clientSerialNumber = content.getSerialNumber();
            latestNotifySerialNumber = content.getSerialNumber();

            ctx.write(CacheResponsePdu.of(content.getSessionId()));
            for (RtrDataUnit dataUnit : content.getAnnouncements()) {
                ctx.write(dataUnit.toPdu(Flags.ANNOUNCEMENT));
            }
            return ctx.write(EndOfDataPdu.of(content.getSessionId(), content.getSerialNumber(), REFRESH_INTERVAL, RETRY_INTERVAL, EXPIRE_INTERVAL));
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            if (cause instanceof PduParseException) {
                PduParseException e = (PduParseException) cause;
                ctx.write(e.getErrorPdu());
            } else {
                ctx.write(ErrorPdu.of(ErrorCode.PduInternalError, new byte[0], "internal error"));
            }
            log.error("Something bad happened", cause);
            ctx.close();
        }

        @Override
        public synchronized SerialNumber getClientSerialNumber() {
            return clientSerialNumber;
        }

        @Override
        public synchronized DateTime getLastActive() {
            return lastActive;
        }

        @Override
        public synchronized void cacheUpdated(short sessionId, SerialNumber updatedSerialNumber) {
            if (!updatedSerialNumber.equals(clientSerialNumber) && !latestNotifySerialNumber.equals(updatedSerialNumber)) {
                if (currentRequest == null) {
                    latestNotifySerialNumber = updatedSerialNumber;
                    ctx.writeAndFlush(NotifyPdu.of(sessionId, updatedSerialNumber));
                }
            }
        }
    }
}
