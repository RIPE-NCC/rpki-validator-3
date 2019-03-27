package net.ripe.rpki.validator3.storage.stores.impl;

import lombok.Getter;
import net.ripe.rpki.validator3.storage.Lmdb;
import net.ripe.rpki.validator3.storage.lmdb.LmdbTests;
import net.ripe.rpki.validator3.storage.lmdb.Tx;
import net.ripe.rpki.validator3.storage.stores.RpkiObjectStore;
import net.ripe.rpki.validator3.storage.stores.RpkiRepositoryStore;
import net.ripe.rpki.validator3.storage.stores.TrustAnchorStore;
import net.ripe.rpki.validator3.storage.stores.ValidationRunStore;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

import java.util.function.Consumer;
import java.util.function.Function;

public class GenericStorageTest {

    @Rule
    public final TemporaryFolder tmp = new TemporaryFolder();

    @Getter
    private RpkiObjectStore rpkiObjectStore;

    @Getter
    private RpkiRepositoryStore rpkiRepositoryStore;

    @Getter
    private TrustAnchorStore trustAnchorStore;

    @Getter
    private ValidationRunStore validationRunStore;

    @Getter
    private Lmdb lmdb;

    @Before
    public void setUp() throws Exception {
        lmdb = LmdbTests.makeLmdb(tmp.newFolder().getAbsolutePath());
        rpkiObjectStore = new LmdbRpkiObject(lmdb);
        rpkiRepositoryStore = new LmdbRpkiRepostiories(lmdb);
        trustAnchorStore = new LmdbTrustAnchors(lmdb);
        validationRunStore = new LmdbValidationRuns(lmdb, trustAnchorStore);
    }

    protected <T> T rtx(Function<Tx.Read, T> f) {
        return Tx.rwith(getLmdb().readTx(), f);
    }

    protected <T> T wtx(Function<Tx.Write, T> f) {
        return Tx.with(getLmdb().writeTx(), f);
    }

    protected void rtx0(Consumer<Tx.Read> f) {
        Tx.ruse(getLmdb().readTx(), f);
    }

    protected void wtx0(Consumer<Tx.Write> f) {
        Tx.use(getLmdb().writeTx(), f);
    }
}
