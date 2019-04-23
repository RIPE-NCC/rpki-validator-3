package net.ripe.rpki.validator3.storage.data.coders;

import net.ripe.rpki.validator3.storage.Bytes;
import net.ripe.rpki.validator3.storage.data.RpkiObject;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class RpkiObjectCoder {

    byte[] toBytes(RpkiObject rpkiObject) {
        String name = rpkiObject.getType().name();
        byte[] sha256 = rpkiObject.getSha256();
        byte[] aki = rpkiObject.getAuthorityKeyIdentifier();
        long epochMillis = rpkiObject.getLastMarkedReachableAt().toEpochMilli();
        byte[] serial = rpkiObject.getSerialNumber().toByteArray();
//        rpkiObject.getLocations().stream().map(loc -> )
        return null;
    }

    RpkiObject fromBytes(byte[] bytes) {
        ByteBuffer bb = ByteBuffer.wrap(bytes);
        return null;
    }


    private static byte[] encode(String s) {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        ByteBuffer bb = ByteBuffer.allocate(Integer.BYTES + bytes.length);
        bb.putInt(bytes.length);
        bb.put(bytes);
        return Bytes.toBytes(bb);
    }

}
