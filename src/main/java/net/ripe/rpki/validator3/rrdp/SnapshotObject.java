package net.ripe.rpki.validator3.rrdp;

public class SnapshotObject extends RepoObject {
    private String uri;

    public SnapshotObject(byte[] content, String uri) {
        super(content);
        this.uri = uri;
    }

    public String getUri() {
        return uri;
    }
}
