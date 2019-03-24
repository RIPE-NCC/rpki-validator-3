package net.ripe.rpki.validator3.storage.stores.impl;

import net.ripe.rpki.validator3.storage.Lmdb;
import net.ripe.rpki.validator3.storage.lmdb.LmdbTests;
import net.ripe.rpki.validator3.storage.lmdb.Tx;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.math.BigInteger;

import static org.junit.Assert.assertEquals;

public class SequencesTest {

    @Rule
    public final TemporaryFolder tmp = new TemporaryFolder();

    private Lmdb env;
    private Sequences sequences;

    @Before
    public void setUp() throws Exception {
        env = LmdbTests.makeLmdb(tmp.newFolder().getAbsolutePath());
        this.sequences = new Sequences(env);
    }

    @Test
    public void testNext() {
        assertEquals(new BigInteger("1"), Tx.with(env.writeTx(), tx -> sequences.next(tx, "seq1")));
        assertEquals(new BigInteger("2"), Tx.with(env.writeTx(), tx -> sequences.next(tx, "seq1")));
        assertEquals(new BigInteger("1"), Tx.with(env.writeTx(), tx -> sequences.next(tx, "seq2")));
        assertEquals(new BigInteger("2"), Tx.with(env.writeTx(), tx -> sequences.next(tx, "seq2")));
        assertEquals(new BigInteger("3"), Tx.with(env.writeTx(), tx -> sequences.next(tx, "seq2")));
    }

}