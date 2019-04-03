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

import com.google.common.collect.ImmutableMap;
import net.ripe.rpki.validator3.storage.Bytes;
import net.ripe.rpki.validator3.storage.Coder;
import net.ripe.rpki.validator3.storage.Lmdb;
import net.ripe.rpki.validator3.storage.data.Key;
import net.ripe.rpki.validator3.storage.lmdb.IxMap;
import net.ripe.rpki.validator3.storage.lmdb.Tx;
import net.ripe.rpki.validator3.storage.stores.SettingsStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;

import static java.nio.charset.StandardCharsets.UTF_8;

@Component
public class LmdbSettings implements SettingsStore {

    private static final String PRECONFIGURED_TAL_SETTINGS_KEY = "internal.preconfigured.tals.loaded";
    private static final String INITIAL_VALIDATION_RUN_COMPLETED = "internal.initial.validation.run.completed";
    private static final String SETTINGS = "settings";

    private final IxMap<String> ixMap;

    @Autowired
    public LmdbSettings(Lmdb lmdb) {
        this.ixMap = new IxMap<>(
                lmdb.getEnv(),
                SETTINGS,
                new Coder<String>() {
                    @Override
                    public ByteBuffer toBytes(String s) {
                        return Bytes.toDirectBuffer(s.getBytes(UTF_8));
                    }

                    @Override
                    public String fromBytes(ByteBuffer bb) {
                        return new String(Bytes.toBytes(bb), UTF_8);
                    }
                },
                ImmutableMap.of());
    }

    @Override
    public void markPreconfiguredTalsLoaded(Tx.Write tx) {
        setTrue(tx, PRECONFIGURED_TAL_SETTINGS_KEY);
    }

    @Override
    public boolean isPreconfiguredTalsLoaded(Tx.Read tx) {
        return isTrue(tx, PRECONFIGURED_TAL_SETTINGS_KEY);
    }

    @Override
    public void markInitialValidationRunCompleted(Tx.Write tx) {
        setTrue(tx, INITIAL_VALIDATION_RUN_COMPLETED);
    }

    @Override
    public boolean isInitialValidationRunCompleted(Tx.Read tx) {
        return isTrue(tx, INITIAL_VALIDATION_RUN_COMPLETED);
    }

    public void setTrue(Tx.Write tx, String preconfiguredTalSettingsKey) {
        ixMap.put(tx, Key.of(preconfiguredTalSettingsKey), "true");
    }

    private boolean isTrue(Tx.Read tx, String initialValidationRunCompleted) {
        return ixMap.get(tx, Key.of(initialValidationRunCompleted)).filter("true"::equals).isPresent();
    }
}
