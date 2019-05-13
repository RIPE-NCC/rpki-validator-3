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
package net.ripe.rpki.validator3.storage.lmdb;

import net.ripe.rpki.validator3.storage.data.Key;
import net.ripe.rpki.validator3.storage.encoding.Coder;
import org.lmdbjava.CursorIterator;
import org.lmdbjava.DbiFlags;
import org.lmdbjava.KeyRange;

import java.io.Serializable;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.lmdbjava.DbiFlags.MDB_CREATE;
import static org.lmdbjava.DbiFlags.MDB_DUPSORT;

public class MultIxMap<T extends Serializable> extends IxBase<T> {

    public MultIxMap(final Lmdb lmdb,
                     final String name,
                     final Coder<T> coder) {
        super(lmdb, name, coder);
    }

    protected DbiFlags[] getMainDbCreateFlags() {
        return new DbiFlags[]{MDB_CREATE, MDB_DUPSORT};
    }

    protected DbiFlags[] getIndexDbiFlags() {
        return new DbiFlags[]{MDB_CREATE, MDB_DUPSORT};
    }

    public List<T> get(Tx.Read txn, Key primaryKey) {
        verifyKey(primaryKey);
        final ByteBuffer pkBuf = primaryKey.toByteBuffer();
        final List<T> result = new ArrayList<>();
        try (final CursorIterator<ByteBuffer> iterate = getMainDb().iterate(txn.txn(), KeyRange.closed(pkBuf, pkBuf))) {
            while (iterate.hasNext()) {
                final CursorIterator.KeyVal<ByteBuffer> next = iterate.next();
                result.add(getValue(primaryKey, next.val()));
            }
        }
        return result;
    }

    public int count(Tx.Read txn, Key primaryKey) {
        verifyKey(primaryKey);
        int s = 0;
        final ByteBuffer pkBuf = primaryKey.toByteBuffer();
        try (final CursorIterator<ByteBuffer> ci = getMainDb().iterate(txn.txn(), KeyRange.closed(pkBuf, pkBuf))) {
            while (ci.hasNext()) {
                ci.next();
                s++;
            }
        }
        return s;
    }

    public void put(Tx.Write tx, Key primaryKey, T value) {
        checkKeyAndValue(primaryKey, value);
        getMainDb().put(tx.txn(), primaryKey.toByteBuffer(), valueBuf(value));
    }

    public void delete(Tx.Write tx, Key primaryKey) {
        getMainDb().delete(tx.txn(), primaryKey.toByteBuffer());
    }

    public void delete(Tx.Write tx, Key primaryKey, T value) {
        verifyKey(primaryKey);
        getMainDb().delete(tx.txn(), primaryKey.toByteBuffer(), valueBuf(value));
    }
}
