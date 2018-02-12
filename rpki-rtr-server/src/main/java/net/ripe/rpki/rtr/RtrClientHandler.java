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
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.traffic.AbstractTrafficShapingHandler;
import lombok.AccessLevel;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.rtr.adapter.netty.ChunkedStream;
import net.ripe.rpki.rtr.domain.RtrCache;
import net.ripe.rpki.rtr.domain.RtrClient;
import net.ripe.rpki.rtr.domain.RtrClients;
import net.ripe.rpki.rtr.domain.SerialNumber;
import net.ripe.rpki.rtr.domain.pdus.CacheResetPdu;
import net.ripe.rpki.rtr.domain.pdus.CacheResponsePdu;
import net.ripe.rpki.rtr.domain.pdus.EndOfDataPdu;
import net.ripe.rpki.rtr.domain.pdus.ErrorCode;
import net.ripe.rpki.rtr.domain.pdus.ErrorPdu;
import net.ripe.rpki.rtr.domain.pdus.Flags;
import net.ripe.rpki.rtr.domain.pdus.NotifyPdu;
import net.ripe.rpki.rtr.domain.pdus.Pdu;
import net.ripe.rpki.rtr.domain.pdus.ProtocolVersion;
import net.ripe.rpki.rtr.domain.pdus.ResetQueryPdu;
import net.ripe.rpki.rtr.domain.pdus.RtrProtocolException;
import net.ripe.rpki.rtr.domain.pdus.SerialQueryPdu;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.stream.Stream;

import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_PROTOTYPE;

@Component
@Scope(SCOPE_PROTOTYPE)
@ToString(exclude = {"cache", "clients"})
@Slf4j
public class RtrClientHandler extends SimpleChannelInboundHandler<Pdu> implements RtrClient {
    private final RtrCache cache;
    private final RtrClients clients;

    @Setter(AccessLevel.PACKAGE)
    @Value("${rtr.client.refresh.interval}")
    private int clientRefreshInterval;

    @Setter(AccessLevel.PACKAGE)
    @Value("${rtr.client.retry.interval}")
    private int clientRetryInterval;

    @Setter(AccessLevel.PACKAGE)
    @Value("${rtr.client.expire.interval}")
    private int clientExpireInterval;

    private ChannelHandlerContext ctx;
    private Pdu currentRequest = null;
    private Queue<Pdu> pending = new ArrayDeque<>();

    private short clientSessionId;
    private SerialNumber clientSerialNumber = SerialNumber.zero();
    private SerialNumber latestNotifySerialNumber = SerialNumber.zero();

    private ProtocolVersion clientProtocolVersion = null;
    private Instant clientConnectedAt = Instant.now();
    private Instant lastRequestReceivedAt = null;

    @Setter
    private AbstractTrafficShapingHandler trafficShapingHandler;

    @Autowired
    public RtrClientHandler(RtrCache cache, RtrClients clients) {
        this.cache = Objects.requireNonNull(cache, "cache");
        this.clients = clients;
        this.clientSessionId = cache.getSessionId();
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
        this.lastRequestReceivedAt = Instant.now();
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

            sendNotifyPduIfNeeded(cache.getSessionId(), cache.getSerialNumber());
        } else {
            ChannelFuture channelFuture = handleClientRequest(ctx, currentRequest);
            if (channelFuture != null) {
                channelFuture.addListener((f) -> requestHandlingCompleted());
            }
        }
    }

    private ChannelFuture handleClientRequest(ChannelHandlerContext ctx, Pdu pdu) {
        log.info("handling client request {}", pdu);
        if (clientProtocolVersion == null) {
            clientProtocolVersion = pdu.getProtocolVersion();
        } else if (clientProtocolVersion != pdu.getProtocolVersion()) {
            throw new RtrProtocolException(ErrorPdu.of(
                clientProtocolVersion,
                ErrorCode.UnexpectedProtocolVersion,
                pdu.toByteArray(),
                String.format("unexpected PDU protocol version %d, negotiated version was %d", pdu.getProtocolVersion().getValue(), clientProtocolVersion.getValue())
            ));
        }

        ChannelFuture responseComplete = null;
        if (pdu instanceof SerialQueryPdu) {
            responseComplete = handleSerialQuery(ctx, (SerialQueryPdu) pdu);
        } else if (pdu instanceof ResetQueryPdu) {
            responseComplete = handleResetQuery(ctx, (ResetQueryPdu) pdu);
        } else if (pdu instanceof ErrorPdu) {
            log.error("error received from client {}, closing connection", pdu);
            ctx.close();
        } else {
            throw new IllegalStateException("unrecognized PDU " + pdu);
        }

        return responseComplete;
    }

    private ChannelFuture handleSerialQuery(ChannelHandlerContext ctx, SerialQueryPdu serialQueryPdu) {
        Either<RtrCache.Delta, RtrCache.Content> deltaOrContent = cache.getDeltaOrContent(serialQueryPdu.getSerialNumber());
        if (deltaOrContent.right().exists(content -> !content.isReady())) {
            return ctx.writeAndFlush(ErrorPdu.of(clientProtocolVersion, ErrorCode.NoDataAvailable, serialQueryPdu.toByteArray(), ""));
        }

        if (deltaOrContent.isLeft()) {
            RtrCache.Delta delta = deltaOrContent.left().value();

            if (delta.getSessionId() != serialQueryPdu.getSessionId()) {
                return ctx.writeAndFlush(CacheResetPdu.of(clientProtocolVersion));
            }

            clientSessionId = delta.getSessionId();
            clientSerialNumber = delta.getSerialNumber();

            ctx.write(CacheResponsePdu.of(clientProtocolVersion, delta.getSessionId()));
            ctx.write(new ChunkedStream<>(delta.getAnnouncements().stream().map(dataUnit -> dataUnit.toPdu(clientProtocolVersion, Flags.ANNOUNCEMENT))));
            ctx.write(new ChunkedStream<>(delta.getWithdrawals().stream().map(dataUnit -> dataUnit.toPdu(clientProtocolVersion, Flags.WITHDRAWAL))));
            return ctx.writeAndFlush(EndOfDataPdu.of(clientProtocolVersion, clientSessionId, clientSerialNumber, clientRefreshInterval, clientRetryInterval, clientExpireInterval));
        } else {
            RtrCache.Content content = deltaOrContent.right().value();
            clientSessionId = content.getSessionId();
            clientSerialNumber = content.getSerialNumber();
            return ctx.writeAndFlush(CacheResetPdu.of(clientProtocolVersion));
        }
    }

    private ChannelFuture handleResetQuery(ChannelHandlerContext ctx, ResetQueryPdu resetQueryPdu) {
        RtrCache.Content content = cache.getCurrentContent();
        if (!content.isReady()) {
            return ctx.writeAndFlush(ErrorPdu.of(clientProtocolVersion, ErrorCode.NoDataAvailable, resetQueryPdu.toByteArray(), ""));
        }

        clientSessionId = content.getSessionId();
        clientSerialNumber = content.getSerialNumber();

        ctx.write(CacheResponsePdu.of(clientProtocolVersion, content.getSessionId()));
        ctx.write(new ChunkedStream<>(content.getAnnouncements().stream().map(dataUnit -> dataUnit.toPdu(clientProtocolVersion, Flags.ANNOUNCEMENT))));
        return ctx.writeAndFlush(EndOfDataPdu.of(clientProtocolVersion, clientSessionId, clientSerialNumber, clientRefreshInterval, clientRetryInterval, clientExpireInterval));
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        Optional<RtrProtocolException> rtrError = Stream
            .iterate(cause, Throwable::getCause)
            .filter(t -> t instanceof RtrProtocolException)
            .map(t -> (RtrProtocolException) t)
            .findFirst();
        if (rtrError.filter(x -> !x.isSendToClient()).isPresent()) {
            log.warn("do not send error in response to client error PDU", rtrError.get());
            ctx.close();
            return;
        }

        ErrorPdu errorPdu = rtrError
            .map(RtrProtocolException::getErrorPdu)
            .orElseGet(() ->
                ErrorPdu.of(
                    clientProtocolVersion == null ? ProtocolVersion.V1 : clientProtocolVersion,
                    ErrorCode.PduInternalError,
                    new byte[0],
                   "")
            );

        ctx.writeAndFlush(errorPdu);
        if (errorPdu.getErrorCode().isFatal()) {
            ctx.close();
        }
    }

    @Override
    public synchronized SerialNumber getClientSerialNumber() {
        return clientSerialNumber;
    }

    private Instant getLastActivityAt() {
        return lastRequestReceivedAt != null ? lastRequestReceivedAt : clientConnectedAt;
    }

    @Override
    public synchronized void cacheUpdated(short sessionId, SerialNumber updatedSerialNumber) {
        sendNotifyPduIfNeeded(sessionId, updatedSerialNumber);
    }

    @Override
    public boolean disconnectIfInactive(Instant now) {
        if (getLastActivityAt().isBefore(now.minusSeconds(clientExpireInterval))) {
            ctx.close();
            return true;
        }

        return false;
    }

    @Override
    public synchronized State getState() {
        return new State(
            ctx.channel().localAddress().toString(),
            ctx.channel().remoteAddress().toString(),
            clientConnectedAt,
            getLastActivityAt(),
            clientProtocolVersion.getValue(),
            clientSessionId,
            clientSerialNumber.getValue(),
            trafficShapingHandler.trafficCounter().cumulativeReadBytes(),
            trafficShapingHandler.trafficCounter().cumulativeWrittenBytes()
        );
    }

    private void sendNotifyPduIfNeeded(short sessionId, SerialNumber updatedSerialNumber) {
        if (currentRequest == null
            && lastRequestReceivedAt != null
            && clientProtocolVersion != null
            && updatedSerialNumber.isAfter(clientSerialNumber)
            && updatedSerialNumber.isAfter(latestNotifySerialNumber)) {
            log.info("Sending notify PDU to client for serial number {}", updatedSerialNumber.getValue());
            latestNotifySerialNumber = updatedSerialNumber;
            ctx.writeAndFlush(NotifyPdu.of(clientProtocolVersion, sessionId, updatedSerialNumber));
        }
    }
}
