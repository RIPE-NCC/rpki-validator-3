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
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageCodec;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.rtr.domain.SerialNumber;
import net.ripe.rpki.rtr.domain.pdus.ErrorCode;
import net.ripe.rpki.rtr.domain.pdus.ErrorPdu;
import net.ripe.rpki.rtr.domain.pdus.Pdu;
import net.ripe.rpki.rtr.domain.pdus.PduParseException;
import net.ripe.rpki.rtr.domain.pdus.ResetQueryPdu;
import net.ripe.rpki.rtr.domain.pdus.SerialQueryPdu;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.BiFunction;

@Slf4j
public class PduCodec extends ByteToMessageCodec<Pdu> {
    @Override
    protected void encode(ChannelHandlerContext ctx, Pdu msg, ByteBuf out) throws Exception {
        log.info("writing {}", msg);
        msg.write(out /*.alloc().buffer(msg.length()) */);
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        if (in.readableBytes() < 8) {
            return;
        }

        in.markReaderIndex();
        BiFunction<ErrorCode, String, ErrorPdu> generateError = (code, text) -> {
            in.resetReaderIndex();
            byte[] content = new byte[in.readableBytes()];
            in.readBytes(content);
            return ErrorPdu.of(code, content, text);
        };

        int protocolVersion = in.readUnsignedByte();
        if (protocolVersion != Pdu.PROTOCOL_VERSION) {
            throw new PduParseException(generateError.apply(
                ErrorCode.UnsupportedProtocolVersion,
                String.format("protocol version must be %d, was %d", Pdu.PROTOCOL_VERSION, protocolVersion)
            ));
        }

        int pduType = in.readUnsignedByte();
        switch (pduType) {
            case SerialQueryPdu.PDU_TYPE: {
                short sessionId = in.readShort();
                long length = in.readUnsignedInt();
                if (length != SerialQueryPdu.PDU_LENGTH) {
                    throw new PduParseException(generateError.apply(
                        ErrorCode.InvalidRequest,
                        String.format("length of Serial Query PDU must be %d, was %d", SerialQueryPdu.PDU_LENGTH, length)
                    ));
                }
                if (in.readableBytes() + 8 < length) {
                    return;
                }
                int serialNumber = in.readInt();
                out.add(SerialQueryPdu.of(sessionId, SerialNumber.of(serialNumber)));
                break;
            }
            case ResetQueryPdu.PDU_TYPE: {
                @SuppressWarnings("unused") int zero = in.readUnsignedShort();
                long length = in.readUnsignedInt();
                if (length != ResetQueryPdu.PDU_LENGTH) {
                    throw new PduParseException(generateError.apply(
                        ErrorCode.InvalidRequest,
                        String.format("length of Reset Query PDU must be %d, was %d", ResetQueryPdu.PDU_LENGTH, length)
                    ));
                }
                out.add(ResetQueryPdu.of());
                break;
            }
            case ErrorPdu.PDU_TYPE: {
                int errorCode = in.readUnsignedShort();
                long length = in.readUnsignedInt();
                if (length > Pdu.MAX_LENGTH) {
                    throw new PduParseException(generateError.apply(
                       ErrorCode.InvalidRequest,
                       String.format("maximum PDU length is %d, was %d", Pdu.MAX_LENGTH, length)
                    ));
                }
                if (in.readableBytes() + 8 < length) {
                    return;
                }
                long encapsulatedPduLength = in.readUnsignedInt();
                if (encapsulatedPduLength > length - 16) {
                    throw new PduParseException(generateError.apply(
                       ErrorCode.InvalidRequest,
                       String.format("encapsulated PDU length %d exceeds maximum length %d", encapsulatedPduLength, length - 16)
                    ));
                }
                byte[] encapsulatedPdu = new byte[(int) encapsulatedPduLength];
                in.readBytes(encapsulatedPdu);

                long errorTextLength = in.readUnsignedInt();
                if (errorTextLength > length - encapsulatedPduLength - 16) {
                    throw new PduParseException(generateError.apply(
                        ErrorCode.InvalidRequest,
                        String.format("error text length %d exceeds maximum length %d", errorTextLength, length - encapsulatedPduLength - 16)
                    ));
                }
                byte[] errorTextBytes = new byte[(int) errorTextLength];
                in.readBytes(errorTextBytes);

                out.add(ErrorPdu.of(ErrorCode.of(errorCode), encapsulatedPdu, new String(errorTextBytes, StandardCharsets.UTF_8)));
                break;
            }
            default:
                throw new PduParseException(generateError.apply(
                    ErrorCode.UnsupportedPduType,
                    String.format("unsupported PDU type %d", pduType)
                ));
        }
    }
}
