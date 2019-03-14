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
package net.ripe.rpki.validator3.storage;

import com.google.common.collect.ImmutableMap;
import lombok.Getter;
import net.ripe.rpki.validator3.storage.data.RpkiObject;
import net.ripe.rpki.validator3.storage.lmdb.Key;
import net.ripe.rpki.validator3.storage.lmdb.Store;
import org.lmdbjava.Env;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.ByteBuffer;

import static org.lmdbjava.Env.create;

@Component
public class Lmdb {

    private final Env<ByteBuffer> env;

    @Getter
    private Store<RpkiObject> rpkiObjectStore;

    public Lmdb() throws Exception {
        // TODO make it configurable
        String path = "/tmp/rpki-validator-3-lmdb";
        env = create()
                .setMapSize(4 * 1024 * 1024 * 1024L)
                .setMaxDbs(100)
                .open(new File(path));

        rpkiObjectStore = createRpkiObjectStore();
    }

    private Store<RpkiObject> createRpkiObjectStore() throws Exception {
        return new Store<>(env,
                RpkiObject.class.getName(),
                new FSTSerializer<>(),
                ImmutableMap.of(
                        "by-sha256", rpkiObject -> new Key(Bytes.toDirectBuffer(rpkiObject.getSha256())),
                        "by-aki", rpkiObject -> new Key(Bytes.toDirectBuffer(rpkiObject.getAuthorityKeyIdentifier()))
                ));
    }
}
