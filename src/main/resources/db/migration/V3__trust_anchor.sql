CREATE TABLE trust_anchor (
    id BIGINT NOT NULL,
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
