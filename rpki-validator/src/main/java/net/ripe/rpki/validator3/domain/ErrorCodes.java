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
package net.ripe.rpki.validator3.domain;

public class ErrorCodes {
    public static final String RRDP_FETCH = "rrdp.fetch";
    public static final String RRDP_FETCH_DELTAS = "rrdp.fetch.deltas";
    public static final String RRDP_PARSE_ERROR = "rrdp.parse.error";
    public static final String RRDP_WRONG_SNAPSHOT_HASH = "rrdp.wrong.snapshot.hash";
    public static final String RRDP_WRONG_SNAPSHOT_SESSION = "rrdp.wrong.snapshot.session";
    public static final String RRDP_WRONG_DELTA_HASH = "rrdp.wrong.delta.hash";
    public static final String RRDP_WRONG_DELTA_SESSION = "rrdp.wrong.delta.session";
    public static final String RRDP_SERIAL_MISMATCH = "rrdp.serial.mismatch";
    public static final String RRDP_REPLACE_NONEXISTENT_OBJECT = "rrdp.replace.nonexistent.object";
    public static final String RRDP_WITHDRAW_NONEXISTENT_OBJECT = "rrdp.withdraw.nonexistent.object";
    public static final String RRDP_SNAPSHOT_FETCH_LOCAL_AHEAD = "rrdp.fetch.snapshot.local.ahead";
    public static final String RRDP_SNAPSHOT_FETCH_NEW_SESSION = "rrdp.fetch.snapshot.new.session";
    public static final String RRDP_CORRUPTED_SNAPSHOT = "rrdp.corrupted.snapshot";


    public static final String RSYNC_FETCH = "rsync.fetch";
    public static final String RSYNC_REPOSITORY_IO = "rsync.repository.io";

    public static final String TRUST_ANCHOR_FETCH = "trust.anchor.fetch";

    public static final String TRUST_ANCHOR_SIGNATURE = "trust.anchor.signature";

    public static final String REPOSITORY_OBJECT_MINIMUM_SIZE = "repository.object.minimum.size";
    public static final String REPOSITORY_OBJECT_MAXIMUM_SIZE = "repository.object.maximum.size";
    public static final String REPOSITORY_OBJECT_IS_TRUST_ANCHOR_CERTIFICATE = "repository.object.is.trust.anchor.certificate";

    public static final String MANIFEST_ALL_ENTRIES_VALID = "manifest.all.entries.valid";

    public static final String UNHANDLED_EXCEPTION = "unhandled.exception";

    private ErrorCodes() {
    }
}
