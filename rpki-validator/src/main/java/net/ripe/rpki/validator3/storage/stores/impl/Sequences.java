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

import com.google.common.primitives.Longs;
import net.ripe.rpki.validator3.storage.Bytes;
import net.ripe.rpki.validator3.storage.lmdb.Lmdb;
import net.ripe.rpki.validator3.storage.data.Key;
import net.ripe.rpki.validator3.storage.encoding.Coder;
import net.ripe.rpki.validator3.storage.lmdb.IxMap;
import net.ripe.rpki.validator3.storage.lmdb.Tx;
import net.ripe.rpki.validator3.storage.stores.GenericStoreImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.util.Optional;

@Component
public class Sequences extends GenericStoreImpl<Long> {

    private final String SEQUENCES = "sequences";
    private final IxMap<Long> ixMap;

    @Autowired
    public Sequences(Lmdb lmdb) {
        this.ixMap = new IxMap<>(
                lmdb,
                SEQUENCES,
                new Coder<Long>() {
                    @Override
                    public ByteBuffer toBytes(Long bigInteger) {
                        return Bytes.toDirectBuffer(Longs.toByteArray(bigInteger));
                    }

                    @Override
                    public Long fromBytes(ByteBuffer bb) {
                        return Longs.fromByteArray(Bytes.toBytes(bb));
                    }
                });
    }


    public long next(Tx.Write tx, String name) {
        final Key key = Key.of(name);
        final Optional<Long> seqValue = ixMap.get(tx, key);
        if (seqValue.isPresent()) {
            final long nextValue = seqValue.get() + 1;
            ixMap.put(tx, key, nextValue);
            return nextValue;
        }
        ixMap.put(tx, key, 1L);
        return 1L;
    }

    @Override
    protected IxMap<Long> ixMap() {
        return ixMap;
    }
}
