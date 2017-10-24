package net.ripe.rpki.validator3.rrdp;

public class DeltaElement {
    protected String uri;

    public DeltaElement(String uri) {
        this.uri = uri;
    }

    public String getUri() {
        return uri;
    }
}
