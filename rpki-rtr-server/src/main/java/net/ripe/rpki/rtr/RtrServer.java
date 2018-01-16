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
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.rtr.adapter.netty.PduCodec;
import net.ripe.rpki.rtr.domain.RtrCache;
import net.ripe.rpki.rtr.domain.RtrClients;
import net.ripe.rpki.rtr.domain.RtrDataUnit;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.ArrayDeque;
import java.util.Objects;
import java.util.Queue;


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
                            ch.pipeline().addLast(new PduCodec(), new RtrServerHandler(rtrCache));
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

    public class RtrServerHandler extends SimpleChannelInboundHandler<Pdu> {
        private static final int REFRESH_INTERVAL = 3600;
        private static final int RETRY_INTERVAL = 600;
        private static final int EXPIRE_INTERVAL = 7200;

        private volatile boolean busy = false;
        private Queue<Pdu> pending = new ArrayDeque<>();
        private final RtrCache rtrCache;
        private int currentSerialNumber = 0;

        public RtrServerHandler(RtrCache rtrCache) {
            this.rtrCache = Objects.requireNonNull(rtrCache, "rtrCache");
        }

        @Override
        public synchronized void channelRead0(ChannelHandlerContext ctx, Pdu msg) {
            clients.register(ctx);

            pending.add(msg);
            if (busy) {
                log.info("queuing request {}", msg);
                return;
            }

            busy = true;

            Pdu pdu = pending.remove();

            ChannelFuture responseComplete = handleRouterRequest(ctx, pdu);
            if (responseComplete != null) {
                responseComplete.addListener(getFutureGenericFutureListener(ctx, pdu));
            }
        }

        private GenericFutureListener<Future<Void>> getFutureGenericFutureListener(ChannelHandlerContext ctx, Pdu pdu) {
            return (f) -> {
                synchronized (this) {
                    Pdu next = pending.poll();
                    if (next == null) {
                        log.info("finished processing pending requests for {}", ctx);
                        busy = false;

                        int cacheSerialNumber = rtrCache.getSerialNumber();
                        if (cacheSerialNumber != currentSerialNumber) {
                            log.info("Serial number updated since the last notification from {} to {}", currentSerialNumber, cacheSerialNumber);
                            ctx.write(NotifyPdu.of(cacheSerialNumber, rtrCache.getSessionId()));
                        }
                    } else {
                        ChannelFuture channelFuture = handleRouterRequest(ctx, pdu);
                        if (channelFuture != null) {
                            channelFuture.addListener(getFutureGenericFutureListener(ctx, pdu));
                        }
                    }
                }
            };
        }

        private ChannelFuture handleRouterRequest(ChannelHandlerContext ctx, Pdu pdu) {
            log.info("processing {}", pdu);
            ChannelFuture responseComplete = null;
            if (pdu instanceof SerialQueryPdu) {
                SerialQueryPdu serialQueryPdu = (SerialQueryPdu) pdu;
                responseComplete = handleSerialQuery(ctx, serialQueryPdu);
            } else if (pdu instanceof ResetQueryPdu) {
                responseComplete = handleResetQuery(ctx);
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
            Either<RtrCache.Delta, RtrCache.Content> deltaOrContent = rtrCache.getDeltaOrContent(serialQueryPdu.getSerialNumber());

            if (deltaOrContent.isLeft()) {
                RtrCache.Delta delta = deltaOrContent.left().value();
                if (delta.getSessionId() != serialQueryPdu.getSessionId()) {
                    return ctx.write(CacheResetPdu.of());
                }

                currentSerialNumber = delta.getSerialNumber();

                ctx.write(CacheResponsePdu.of(delta.getSessionId()));
                for (RtrDataUnit dataUnit : delta.getAnnouncements()) {
                    ctx.write(dataUnit.toPdu(Flags.ANNOUNCEMENT));
                }
                for (RtrDataUnit dataUnit : delta.getWithdrawals()) {
                    ctx.write(dataUnit.toPdu(Flags.WITHDRAWAL));
                }
                return ctx.write(EndOfDataPdu.of(delta.getSessionId(), delta.getSerialNumber(), REFRESH_INTERVAL, RETRY_INTERVAL, EXPIRE_INTERVAL));
            } else {
                return ctx.write(CacheResetPdu.of());
            }
        }

        private ChannelFuture handleResetQuery(ChannelHandlerContext ctx) {
            RtrCache.Content content = rtrCache.getCurrentContent();

            currentSerialNumber = content.getSerialNumber();

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
    }
}
