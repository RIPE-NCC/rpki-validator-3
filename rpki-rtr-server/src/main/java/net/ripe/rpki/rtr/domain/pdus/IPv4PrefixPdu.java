package net.ripe.rpki.rtr.domain.pdus;

import io.netty.buffer.ByteBuf;
import lombok.Value;

/**
 * @see <a href="https://tools.ietf.org/html/rfc8210#section-5.6">RFC8210 section 5.6 - IPv4 Prefix</a>
 */
@Value(staticConstructor = "of")
public class IPv4PrefixPdu implements Pdu {
    public static final int PDU_TYPE = 4;

    byte prefixLength;
    byte maxLength;
    byte[] prefix;
    int asn;

    public void write(Flags flags, ByteBuf out) {
        out
            .writeByte(PROTOCOL_VERSION)
            .writeByte(PDU_TYPE)
            .writeShort(0)
            .writeInt(20)
            .writeByte(flags.getFlags())
            .writeByte(prefixLength)
            .writeByte(maxLength)
            .writeByte(0)
            .writeBytes(prefix)
            .writeInt(asn);
    }
}
