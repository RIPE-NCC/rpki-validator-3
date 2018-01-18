package net.ripe.rpki.rtr.domain.pdus;

import io.netty.buffer.ByteBuf;
import lombok.Value;
import net.ripe.ipresource.Asn;
import org.bouncycastle.asn1.x509.SubjectKeyIdentifier;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;

import java.io.IOException;

@Value(staticConstructor = "of")
public class RouterKeyPdu implements Pdu {
    public static final int PDU_TYPE = 9;

    Flags flags;
    SubjectKeyIdentifier subjectKeyIdentifier;
    SubjectPublicKeyInfo subjectPublicKeyInfo;
    Asn asn;

    @Override
    public int length() {
        return 24 + keyInfo().length;
    }

    private byte[] keyInfo() {
        try {
            return subjectPublicKeyInfo.getEncoded();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void write(ByteBuf out) {
        out
                .writeByte(PROTOCOL_VERSION)
                .writeByte(PDU_TYPE)
                .writeByte(flags.getFlags())
                .writeByte(0)
                .writeInt(length())
                .writeBytes(subjectKeyIdentifier.getKeyIdentifier())
                .writeInt(asn.getValue().intValue())
                .writeBytes(keyInfo());

    }
}
