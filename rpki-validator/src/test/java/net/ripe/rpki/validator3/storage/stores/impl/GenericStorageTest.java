/**
 * The BSD License
 *
 * Copyright (c) 2010-2018 RIPE NCC
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *   - Redistributions of source code must retain the above copyright notice,
 *     this list of conditions and the following disclaimer.
 *   - Redistributions in binary form must reproduce the above copyright notice,
 *     this list of conditions and the following disclaimer in the documentation
 *     and/or other materials provided with the distribution.
 *   - Neither the name of the RIPE NCC nor the names of its contributors may be
 *     used to endorse or promote products derived from this software without
 *     specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
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
        validationRunStore = new LmdbValidationRuns(lmdb, trustAnchorStore, rpkiObjectStore);
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
