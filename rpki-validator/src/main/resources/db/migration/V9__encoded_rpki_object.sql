--
-- The BSD License
--
-- Copyright (c) 2010-2018 RIPE NCC
-- All rights reserved.
--
-- Redistribution and use in source and binary forms, with or without
-- modification, are permitted provided that the following conditions are met:
--   - Redistributions of source code must retain the above copyright notice,
--     this list of conditions and the following disclaimer.
--   - Redistributions in binary form must reproduce the above copyright notice,
--     this list of conditions and the following disclaimer in the documentation
--     and/or other materials provided with the distribution.
--   - Neither the name of the RIPE NCC nor the names of its contributors may be
--     used to endorse or promote products derived from this software without
--     specific prior written permission.
--
-- THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
-- AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
-- IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
-- ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
-- LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
-- CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
-- SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
-- INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
-- CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
-- ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
-- POSSIBILITY OF SUCH DAMAGE.
--

UPDATE rpki_repository SET last_downloaded_at = NULL, rrdp_session_id = NULL, rrdp_serial = NULL, status = 'PENDING';

DELETE FROM rpki_object;

CREATE TABLE encoded_rpki_object (
    id BIGINT NOT NULL,
    version INTEGER NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    rpki_object_id BIGINT,
    encoded BINARY NOT NULL,
    CONSTRAINT encoded_rpki_object__pk PRIMARY KEY (id),
    CONSTRAINT encoded_rpki_object__rpki_object_id_unique UNIQUE (rpki_object_id ASC),
    CONSTRAINT encoded_rpki_object__rpki_object_id_fk FOREIGN KEY (rpki_object_id) REFERENCES rpki_object (id) ON DELETE CASCADE
);

ALTER TABLE rpki_object DROP COLUMN encoded;
