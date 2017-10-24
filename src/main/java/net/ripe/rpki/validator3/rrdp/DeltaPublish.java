package net.ripe.rpki.validator3.rrdp;

import java.util.Optional;

public class DeltaPublish extends DeltaElement {

    private String hash;
    private byte[] content;

    public DeltaPublish(byte[] content, String uri, String hash) {
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

    public Optional<String> getHash() {
        return Optional.ofNullable(hash);
    }

    public byte[] getContent() {
        return content;
    }
}
