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
import net.ripe.rpki.validator3.background.ValidationScheduler;
import net.ripe.rpki.validator3.storage.lmdb.Lmdb;
import net.ripe.rpki.validator3.storage.lmdb.LmdbTx;
import net.ripe.rpki.validator3.storage.stores.RpkiObjects;
import net.ripe.rpki.validator3.storage.stores.RpkiRepositories;
import net.ripe.rpki.validator3.storage.stores.Settings;
import net.ripe.rpki.validator3.storage.stores.TrustAnchors;
import net.ripe.rpki.validator3.storage.stores.ValidationRuns;
import org.junit.Before;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.function.Consumer;
import java.util.function.Function;

public class GenericStorageTest {

    @Autowired
    @Getter
    private RpkiObjects rpkiObjects;

    @Autowired
    @Getter
    private RpkiRepositories rpkiRepositories;

    @Autowired
    @Getter
    private TrustAnchors trustAnchors;

    @Autowired
    @Getter
    private ValidationRuns validationRuns;

    @Autowired
    @Getter
    private ValidationScheduler validationScheduler;

    @Autowired
    @Getter
    private Sequences sequences;

    @Autowired
    @Getter
    private Settings settings;

    @Getter
    @Autowired
    private Lmdb lmdb;

    @Before
    public void setUp() throws Exception {
        wtx0(tx -> {
            rpkiObjects.clear(tx);
            trustAnchors.clear(tx);
            rpkiRepositories.clear(tx);
            validationRuns.clear(tx);
            sequences.clear(tx);
            settings.clear(tx);
        });
    }

    protected <T> T rtx(Function<LmdbTx.Read, T> f) {
        return getLmdb().readTx(f);
    }

    protected <T> T wtx(Function<LmdbTx.Write, T> f) {
        return getLmdb().writeTx(f);
    }

    protected void rtx0(Consumer<LmdbTx.Read> f) {
        getLmdb().readTx0(f);
    }

    protected void wtx0(Consumer<LmdbTx.Write> f) {
        getLmdb().writeTx0(f);
    }
}
