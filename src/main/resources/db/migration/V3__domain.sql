CREATE TABLE trust_anchor (
    id BIGINT NOT NULL,
    version INTEGER NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    name VARCHAR_IGNORECASE(1000) NOT NULL,
    subject_public_key_info VARCHAR(2000) NOT NULL,
    CONSTRAINT trust_anchor__pk PRIMARY KEY (id)
);
CREATE INDEX trust_anchor__name ON trust_anchor (name ASC);

CREATE TABLE trust_anchor_locations (
    trust_anchor_id BIGINT NOT NULL,
    locations_order INT NOT NULL,
    locations VARCHAR(16000) NOT NULL,
    CONSTRAINT trust_anchor_locations__pk PRIMARY KEY (trust_anchor_id, locations_order),
    CONSTRAINT trust_anchor_locations__trust_anchor_fk FOREIGN KEY (trust_anchor_id) REFERENCES trust_anchor (id) ON DELETE CASCADE
);

CREATE TABLE validation_run (
    id BIGINT NOT NULL,
    version INTEGER NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    trust_anchor_id BIGINT NOT NULL,
    trust_anchor_certificate_uri VARCHAR(16000) NOT NULL,
    CONSTRAINT validation_run__pk PRIMARY KEY (id),
    CONSTRAINT validation_run__trust_anchor_fk FOREIGN KEY (trust_anchor_id) REFERENCES trust_anchor (id) ON DELETE RESTRICT
);
CREATE INDEX validation_run__trust_anchor_id_idx ON validation_run (trust_anchor_id ASC, created_at DESC);
