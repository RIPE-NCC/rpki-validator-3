package net.ripe.rpki.rtr.domain.pdus;

public enum Flags {
    ANNOUNCEMENT(1), WITHDRAWAL(0);

    private int flags;

    Flags(int flags) {
        this.flags = flags;
    }

    public int getFlags() {
        return flags;
    }
}
