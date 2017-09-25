CREATE TABLE trust_anchor (
    id BIGINT NOT NULL,
    name VARCHAR_IGNORECASE(1000) NOT NULL,
    rsync_uri VARCHAR(16000) NOT NULL,
    subject_public_key_info VARCHAR(2000) NOT NULL,
    CONSTRAINT pk PRIMARY KEY (id)
);
CREATE INDEX trust_anchor__name ON trust_anchor (name ASC);
