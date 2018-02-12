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
package net.ripe.rpki.rtr.adapter.netty;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageCodec;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.rtr.domain.RtrPrefix;
import net.ripe.rpki.rtr.domain.SerialNumber;
import net.ripe.rpki.rtr.domain.pdus.CacheResetPdu;
import net.ripe.rpki.rtr.domain.pdus.CacheResponsePdu;
import net.ripe.rpki.rtr.domain.pdus.EndOfDataPdu;
import net.ripe.rpki.rtr.domain.pdus.ErrorCode;
import net.ripe.rpki.rtr.domain.pdus.ErrorPdu;
import net.ripe.rpki.rtr.domain.pdus.Flags;
import net.ripe.rpki.rtr.domain.pdus.IPv4PrefixPdu;
import net.ripe.rpki.rtr.domain.pdus.IPv6PrefixPdu;
import net.ripe.rpki.rtr.domain.pdus.NotifyPdu;
import net.ripe.rpki.rtr.domain.pdus.Pdu;
import net.ripe.rpki.rtr.domain.pdus.ProtocolVersion;
import net.ripe.rpki.rtr.domain.pdus.ResetQueryPdu;
import net.ripe.rpki.rtr.domain.pdus.RtrProtocolException;
import net.ripe.rpki.rtr.domain.pdus.SerialQueryPdu;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;

import static net.ripe.rpki.rtr.domain.pdus.ProtocolVersion.V0;
import static net.ripe.rpki.rtr.domain.pdus.ProtocolVersion.V1;

@Slf4j
public class PduCodec extends ByteToMessageCodec<Pdu> {
    @Override
    protected void encode(ChannelHandlerContext ctx, Pdu msg, ByteBuf out) throws Exception {
        log.info("writing {}", msg);
        msg.write(out /*.alloc().buffer(msg.length()) */);
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        parsePdu(in).ifPresent(out::add);
    }

    public static Optional<Pdu> parsePdu(ByteBuf in) {
        if (in.readableBytes() < 8) {
            return Optional.empty();
        }

        in.markReaderIndex();
        byte protocolVersionValue = in.readByte();
        ProtocolVersion protocolVersion = ProtocolVersion.of(protocolVersionValue);

        BiFunction<ErrorCode, String, ErrorPdu> generateError = (code, text) -> {
            in.resetReaderIndex();
            byte[] content = new byte[in.readableBytes()];
            in.readBytes(content);
            return ErrorPdu.of(protocolVersion == null ? V1 : protocolVersion, code, content, text);
        };

        if (protocolVersion == null) {
            throw new RtrProtocolException(generateError.apply(
                ErrorCode.UnsupportedProtocolVersion,
                String.format("protocol version must be 0 or 1, was %d", Byte.toUnsignedInt(protocolVersionValue))
            ));
        }

        int pduType = in.readUnsignedByte();
        switch (pduType) {
            case NotifyPdu.PDU_TYPE: {
                short sessionId = in.readShort();
                if (!checkLength(in, NotifyPdu.PDU_LENGTH, "Serial Notify", generateError)) {
                    return Optional.empty();
                }
                int serialNumber = in.readInt();
                return Optional.of(NotifyPdu.of(protocolVersion, sessionId, SerialNumber.of(serialNumber)));
            }
            case SerialQueryPdu.PDU_TYPE: {
                short sessionId = in.readShort();
                if (!checkLength(in, SerialQueryPdu.PDU_LENGTH, "Serial Query", generateError)) {
                    return Optional.empty();
                }
                int serialNumber = in.readInt();
                return Optional.of(SerialQueryPdu.of(protocolVersion, sessionId, SerialNumber.of(serialNumber)));
            }
            case ResetQueryPdu.PDU_TYPE: {
                in.readUnsignedShort(); /* zero */
                if (!checkLength(in, ResetQueryPdu.PDU_LENGTH, "Reset Query", generateError)) {
                    return Optional.empty();
                }
                return Optional.of(ResetQueryPdu.of(protocolVersion));
            }
            case CacheResponsePdu.PDU_TYPE: {
                short sessionId = in.readShort();
                if (!checkLength(in, CacheResponsePdu.PDU_LENGTH, "Cache Response", generateError)) {
                    return Optional.empty();
                }
                return Optional.of(CacheResponsePdu.of(protocolVersion, sessionId));
            }
            case IPv4PrefixPdu.PDU_TYPE: {
                in.readShort(); /* zero */
                if (!checkLength(in, IPv4PrefixPdu.PDU_LENGTH, "IPv4 Prefix", generateError)) {
                    return Optional.empty();
                }
                Flags flags = (in.readByte() & 0x01) == 0 ? Flags.WITHDRAWAL : Flags.ANNOUNCEMENT;
                byte prefixLength = in.readByte();
                byte maxLength = in.readByte();
                in.readByte(); /* zero */
                byte[] prefix = ByteBufUtil.getBytes(in.readBytes(4));
                int asn = in.readInt();
                return Optional.of(IPv4PrefixPdu.of(protocolVersion, flags, RtrPrefix.of(prefixLength, maxLength, prefix, asn)));
            }
            case IPv6PrefixPdu.PDU_TYPE: {
                in.readShort(); /* zero */
                if (!checkLength(in, IPv6PrefixPdu.PDU_LENGTH, "IPv6 Prefix", generateError)) {
                    return Optional.empty();
                }
                Flags flags = (in.readByte() & 0x01) == 0 ? Flags.WITHDRAWAL : Flags.ANNOUNCEMENT;
                byte prefixLength = in.readByte();
                byte maxLength = in.readByte();
                in.readByte(); /* zero */
                byte[] prefix = ByteBufUtil.getBytes(in.readBytes(16));
                int asn = in.readInt();
                return Optional.of(IPv4PrefixPdu.of(protocolVersion, flags, RtrPrefix.of(prefixLength, maxLength, prefix, asn)));
            }
            case EndOfDataPdu.PDU_TYPE: {
                short sessionId = in.readShort();
                switch (protocolVersion) {
                    case V0: {
                        if (!checkLength(in, EndOfDataPdu.PDU_LENGTH_V0, "End of Data", generateError)) {
                            return Optional.empty();
                        }
                        int serialNumber = in.readInt();
                        return Optional.of(EndOfDataPdu.of(V0, sessionId, SerialNumber.of(serialNumber), 3600, 600, 7200));
                    }
                    case V1: {
                        if (!checkLength(in, EndOfDataPdu.PDU_LENGTH_V1, "End of Data", generateError)) {
                            return Optional.empty();
                        }
                        int serialNumber = in.readInt();
                        int refreshInterval = in.readInt();
                        int retryInterval = in.readInt();
                        int expireInterval = in.readInt();
                        return Optional.of(EndOfDataPdu.of(V1, sessionId, SerialNumber.of(serialNumber), refreshInterval, retryInterval, expireInterval));
                    }
                }
                throw new IllegalStateException("bad protocol version " + protocolVersion);
            }
            case CacheResetPdu.PDU_TYPE: {
                in.readUnsignedShort(); /* zero */
                if (!checkLength(in, CacheResetPdu.PDU_LENGTH, "Cache Reset", generateError)) {
                    return Optional.empty();
                }
                return Optional.of(CacheResetPdu.of(protocolVersion));
            }
            case ErrorPdu.PDU_TYPE: {
                int errorCode = in.readUnsignedShort();
                long length = in.readUnsignedInt();
                if (length > Pdu.MAX_LENGTH) {
                    throw new RtrProtocolException(generateError.apply(
                        ErrorCode.InvalidRequest,
                        String.format("maximum PDU length is %d, was %d", Pdu.MAX_LENGTH, length)
                    ), false);
                }
                if (in.readableBytes() + 8 < length) {
                    return Optional.empty();
                }
                long encapsulatedPduLength = in.readUnsignedInt();
                if (encapsulatedPduLength > length - 16) {
                    throw new RtrProtocolException(generateError.apply(
                        ErrorCode.InvalidRequest,
                        String.format("encapsulated PDU length %d exceeds maximum length %d", encapsulatedPduLength, length - 16)
                    ), false);
                }
                byte[] encapsulatedPdu = new byte[(int) encapsulatedPduLength];
                in.readBytes(encapsulatedPdu);

                long errorTextLength = in.readUnsignedInt();
                if (errorTextLength > length - encapsulatedPduLength - 16) {
                    throw new RtrProtocolException(generateError.apply(
                        ErrorCode.InvalidRequest,
                        String.format("error text length %d exceeds maximum length %d", errorTextLength, length - encapsulatedPduLength - 16)
                    ), false);
                }
                byte[] errorTextBytes = new byte[(int) errorTextLength];
                in.readBytes(errorTextBytes);

                return Optional.of(ErrorPdu.of(protocolVersion, ErrorCode.of(errorCode), encapsulatedPdu, new String(errorTextBytes, StandardCharsets.UTF_8)));
            }
            default:
                throw new RtrProtocolException(generateError.apply(
                    ErrorCode.UnsupportedPduType,
                    String.format("unsupported PDU type %d", pduType)
                ));
        }
    }

    private static boolean checkLength(ByteBuf in, int expectedLength, String pduType, BiFunction<ErrorCode, String, ErrorPdu> generateError) {
        long length = in.readUnsignedInt();
        if (length != expectedLength) {
            throw new RtrProtocolException(generateError.apply(
                ErrorCode.InvalidRequest,
                String.format("length of %s PDU must be %d, was %d", pduType, expectedLength, length)
            ));
        }
        return in.readableBytes() + 8 >= length;
    }
}
