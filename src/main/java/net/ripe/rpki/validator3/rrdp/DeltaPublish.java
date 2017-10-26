package net.ripe.rpki.validator3.rrdp;

import java.util.Optional;

public class DeltaPublish extends DeltaElement {

    private byte[] hash;
    private byte[] content;

    public DeltaPublish(byte[] content, String uri, byte[] hash) {
        super(uri);
        this.content = content;
        this.uri = uri;
        this.hash = hash;
    }

    public DeltaPublish(byte[] content, String uri) {
        super(uri);
        this.content = content;
        this.uri = uri;
    }

    public Optional<byte[]> getHash() {
        return Optional.ofNullable(hash);
    }

    public byte[] getContent() {
        return content;
    }
}
