package net.ripe.rpki.validator3.rrdp;

public class DeltaWithdraw extends DeltaElement {

    private byte[] hash;

    public DeltaWithdraw(String uri, byte[] hash) {
        super(uri);
        this.hash = hash;
    }

    public byte[] getHash() {
        return hash;
    }

}
