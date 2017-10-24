package net.ripe.rpki.validator3.rrdp;

public class DeltaWithdraw extends DeltaElement {

    private String hash;

    public DeltaWithdraw(String uri, String hash) {
        super(uri);
        this.hash = hash;
    }

    public String getHash() {
        return hash;
    }
}
